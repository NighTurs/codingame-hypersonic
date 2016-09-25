package com.github.nighturs.codingame.hypersonic;

import com.github.nighturs.codingame.hypersonic.Player.Board;
import com.github.nighturs.codingame.hypersonic.Player.Bomb;
import com.github.nighturs.codingame.hypersonic.Player.Box;
import com.github.nighturs.codingame.hypersonic.Player.Wall;
import org.junit.Test;

import java.util.Arrays;

import static com.github.nighturs.codingame.hypersonic.Player.Position.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerTest {

    @Test
    public void testBoard() throws Exception {
        /*
        ...CB.....
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
        assertEquals(9, board.nextExplosionInCell(of(1, 9), 2));

        assertEquals(4, board.bombermanBombsUsed(1, 0));
        assertEquals(4, board.bombermanBombsUsed(1, 1));
        assertEquals(0, board.bombermanBombsUsed(1, 2));
        assertEquals(0, board.bombermanBombsUsed(1, 3));
        assertEquals(1, board.bombermanBombsUsed(2, 2));
        assertEquals(0, board.bombermanBombsUsed(2, 9));
    }
}