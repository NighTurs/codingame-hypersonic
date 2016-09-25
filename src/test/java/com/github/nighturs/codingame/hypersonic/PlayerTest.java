package com.github.nighturs.codingame.hypersonic;

import com.github.nighturs.codingame.hypersonic.Player.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.github.nighturs.codingame.hypersonic.Player.Position.of;
import static com.github.nighturs.codingame.hypersonic.Player.planTurn;
import static java.util.Collections.emptyList;
import static org.junit.Assert.*;

public class PlayerTest {

    @Test
    public void testBoard() throws Exception {
        /*
        ...CB.I...
        ..........
        ..B...B...
        ..C.......
        ..C.......
        ..........
        ..B...BWC.
        ..........
        ..C.......
         */
        Board board = Board.createBoard(11,
                11,
                Arrays.asList(new Box(of(1, 4)),
                        new Box(of(4, 3)),
                        new Box(of(5, 3)),
                        new Box(of(10, 3)),
                        new Box(of(7, 9)),
                        new Bomb(0, 2, 5, of(3, 3), 1),
                        new Bomb(2, 2, 5, of(3, 7), 1),
                        new Bomb(3, 2, 5, of(7, 7), 1),
                        new Bomb(3, 3, 5, of(7, 3), 1),
                        new Bomb(5, 4, 10, of(1, 5), 2),
                        new Item(Item.Type.RANGE, of(1, 6)),
                        new Wall(of(7, 8))));
        assertFalse(board.isCellPassable(of(7, 8), 1));
        assertFalse(board.isCellPassable(of(7, 8), 100));
        assertTrue(board.isCellBox(of(4, 3), 0));
        assertTrue(board.isCellBox(of(4, 3), 1));
        assertFalse(board.isCellBox(of(4, 3), 2));
        assertFalse(board.isCellBox(of(4, 3), 3));
        assertTrue(board.isCellBox(of(5, 3), 1));
        assertFalse(board.isCellBox(of(5, 3), 2));
        assertFalse(board.isCellPassable(of(4, 3), 2));
        assertTrue(board.isCellPassable(of(4, 3), 3));
        assertTrue(board.isCellPassable(of(6, 3), 1));
        assertFalse(board.isCellPassable(of(6, 3), 2));
        assertTrue(board.isCellPassable(of(6, 3), 3));
        assertFalse(board.isCellPassable(of(7, 7), 1));
        assertTrue(board.isCellPassable(of(7, 7), 3));
        assertTrue(board.isCellBox(of(7, 9), 100));
        assertTrue(board.isCellBox(of(1, 4), 2));
        assertTrue(board.isCellBox(of(1, 4), 3));
        assertFalse(board.isCellBox(of(1, 4), 9));
        assertFalse(board.isCellVacantForBomb(of(1, 5), 8));
        assertTrue(board.isCellVacantForBomb(of(1, 5), 9));

        assertEquals(2, board.nextExplosionInCell(of(5, 3), 0));
        assertEquals(-1, board.nextExplosionInCell(of(5, 3), 2));
        assertEquals(9, board.nextExplosionInCell(of(1, 5), 2));

        assertEquals(4, board.bombermanBombsUsed(1, 0));
        assertEquals(4, board.bombermanBombsUsed(1, 1));
        assertEquals(4, board.bombermanBombsUsed(1, 2));
        assertEquals(0, board.bombermanBombsUsed(1, 3));
        assertEquals(0, board.bombermanBombsUsed(1, 4));
        assertEquals(1, board.bombermanBombsUsed(2, 2));
        assertEquals(1, board.bombermanBombsUsed(2, 9));
        assertEquals(0, board.bombermanBombsUsed(2, 10));

        assertTrue(board.isCellPassable(of(1, 6), 2));
        assertTrue(board.isCellHasItem(of(1, 6), 2));
        assertFalse(board.isCellHasItem(of(1, 6), 9));
        assertFalse(board.isCellHasItem(of(1, 6), 10));
    }

    @Test
    public void testFarmBoxesStrategy() {
        int n = 3;
        int m = 5;

        List<GameObject> gameObjects;
        GameState gameState;
        /*
        .WCW.
        .P...
        ..W..
         */
        gameObjects = Arrays.asList(new Wall(of(0, 1)), new Wall(of(0, 3)), new Wall(of(2, 2)), new Box(of(0, 2)));
        gameState = new GameState(n, m, new Bomberman(1, of(1, 1), 2, 8, 1, 1), emptyList(), gameObjects);
        assertEquals(new MoveAction(of(1, 2), "BOMB{1,2}"), planTurn(gameState));

        /*
        .WCW.
        ..PW.
        .WWW.
         */
        gameObjects =
                Arrays.asList(new Wall(of(0, 1)),
                        new Wall(of(0, 3)),
                        new Wall(of(2, 2)),
                        new Wall(of(1, 3)),
                        new Wall(of(2, 1)),
                        new Wall(of(2, 3)),
                        new Box(of(0, 2)));
        gameState = new GameState(n, m, new Bomberman(1, of(1, 2), 2, 8, 1, 1), emptyList(), gameObjects);
        assertEquals(new PlaceBombAction(of(1, 1)), planTurn(gameState));

        /*
        B.CW.
        .PCW.
        WWWW.
         */
        gameObjects =
                Arrays.asList(new Wall(of(0, 3)),
                        new Wall(of(1, 3)),
                        new Wall(of(2, 3)),
                        new Wall(of(1, 3)),
                        new Wall(of(2, 0)),
                        new Wall(of(2, 1)),
                        new Wall(of(2, 2)),
                        new Box(of(0, 2)),
                        new Box(of(1, 2)),
                        new Bomb(0, 1, 2, of(0, 0), 1));
        gameState = new GameState(n, m, new Bomberman(1, of(1, 1), 2, 8, 0, 1), emptyList(), gameObjects);
        assertEquals(new MoveAction(of(1, 1), "BOMB{1,1}"), planTurn(gameState));

         /*
        .BCW.
        .PCW.
        WWWW.
         */
        gameObjects =
                Arrays.asList(new Wall(of(0, 3)),
                        new Wall(of(1, 3)),
                        new Wall(of(2, 3)),
                        new Wall(of(1, 3)),
                        new Wall(of(2, 0)),
                        new Wall(of(2, 1)),
                        new Wall(of(2, 2)),
                        new Box(of(0, 2)),
                        new Box(of(1, 2)),
                        new Bomb(0, 1, 2, of(0, 1), 1));
        gameState = new GameState(n, m, new Bomberman(1, of(1, 1), 2, 8, 0, 1), emptyList(), gameObjects);
        assertEquals(new MoveAction(of(1, 0), "BOMB{1,1}"), planTurn(gameState));
    }

    @Test
    public void testSurviveStrategy() {
        int n = 3;
        int m = 5;

        List<GameObject> gameObjects;
        GameState gameState;
        /*
        .WCW.
        .PBW.
        WWW..
         */
        gameObjects = Arrays.asList(new Wall(of(0, 1)),
                new Wall(of(0, 3)),
                new Wall(of(1, 3)),
                new Wall(of(2, 0)),
                new Wall(of(2, 1)),
                new Wall(of(2, 2)),
                new Box(of(0, 2)),
                new Bomb(0, 2, 3, of(1, 2), 0));
        gameState = new GameState(n, m, new Bomberman(1, of(1, 1), 2, 8, 1, 1), emptyList(), gameObjects);
        assertEquals(new MoveAction(of(1, 0)), planTurn(gameState));
    }
}