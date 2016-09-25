package com.github.nighturs.codingame.hypersonic;

import java.util.*;

public class Player {

    static int MAXX = 11;
    static int MAXY = 13;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        int myId = in.nextInt();
        in.nextLine();

        while (true) {
            for (int i = 0; i < height; i++) {
                String row = in.nextLine();
            }
            int entities = in.nextInt();
            for (int i = 0; i < entities; i++) {
                int entityType = in.nextInt();
                int owner = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int param1 = in.nextInt();
                int param2 = in.nextInt();
            }
            in.nextLine();

            System.out.println("BOMB 6 5");
        }
    }

    static class GameState {

    }

    static class Board {

        private static final int INF = Integer.MAX_VALUE;
        final int n;
        final int m;
        final List<GameObject> gameObjects;
        final boolean[][] hasWall;
        final int[][] hasBoxUntil;
        final int[][] hasBombUntil;
        final Map<Position, List<Integer>> explosions;

        public static Board createBoard(int n, int m, List<GameObject> gameObjects) {
            boolean[][] hasWall = new boolean[n][m];
            for (GameObject go : gameObjects) {
                if (go instanceof Wall) {
                    hasWall[go.getPos().getX()][go.getPos().getY()] = true;
                }
            }
            return new Board(n, m, hasWall, gameObjects);
        }

        public static Board appendTimeline(Board board, List<TimelineGameObject> timelineObjects) {
            ArrayList<GameObject> objects = new ArrayList<>(board.gameObjects);
            objects.addAll(timelineObjects);
            return new Board(board.n, board.m, board.hasWall, objects);
        }

        private Board(int n, int m, boolean[][] hasWall, List<GameObject> gameObjects) {
            this.n = n;
            this.m = m;
            this.hasWall = hasWall;
            this.gameObjects = gameObjects;
            this.hasBoxUntil = fill(new int[n][m], -1);
            this.hasBombUntil = fill(new int[n][m], -1);
            explosions = new HashMap<>();
            calcFuture();
        }

        private int[][] fill(int[][] mat, int val) {
            for (int[] a : mat) {
                Arrays.fill(a, val);
            }
            return mat;
        }

        private void calcFuture() {
            final int isEmpty = 0;
            final int isWall = 1;
            final int isBox = 2;
            final int isBomb = 3;
            int[][] curTurnBoard = new int[n][m];
            int[][] nextTurnBoard;
            List<Bomb> bombs = new ArrayList<>();
            Map<Position, List<Bomb>> bombsByPosition = new HashMap<>();
            for (GameObject o : gameObjects) {
                if (o instanceof Wall) {
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isWall;
                } else if (o instanceof Box) {
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isBox;
                    hasBoxUntil[o.getPos().getX()][o.getPos().getY()] = INF;
                } else if (o instanceof Bomb) {
                    bombs.add((Bomb) o);
                    bombsByPosition.putIfAbsent(o.getPos(), new ArrayList<>());
                    bombsByPosition.get(o.getPos()).add((Bomb) o);
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isBomb;
                }
            }
            nextTurnBoard = curTurnBoard.clone();
            Set<Bomb> isDetonated = new HashSet<>();
            while (isDetonated.size() < bombs.size()) {
                int minDetonateTime = Integer.MAX_VALUE;
                int nextBombToDetonate = 0;
                for (int i = 0; i < bombs.size(); i++) {
                    if (!isDetonated.contains(bombs.get(i)) &&
                            bombs.get(i).createTime() + bombs.get(i).getCountdown() < minDetonateTime) {
                        minDetonateTime = bombs.get(i).createTime() + bombs.get(i).getCountdown();
                        nextBombToDetonate = i;
                    }
                }
                Bomb initiatorBomb = bombs.get(nextBombToDetonate);
                isDetonated.add(initiatorBomb);
                hasBombUntil[initiatorBomb.getPos().getX()][initiatorBomb.getPos().getY()] = minDetonateTime - 1;
                Queue<Bomb> toDetonateBombs = new ArrayDeque<>();
                toDetonateBombs.add(initiatorBomb);
                Set<Position> exploadedCells = new HashSet<>();
                while (!toDetonateBombs.isEmpty()) {
                    Bomb curBomb = toDetonateBombs.poll();
                    int x = curBomb.getPos().getX();
                    int y = curBomb.getPos().getY();
                    int range = curBomb.getRange();
                    for (int i = -1; i <= 1; i++) {
                        for (int h = -1; h <= 1; h++) {
                            if (i == 0 ^ h == 0) {
                                for (int d = 0; d < range; d++) {
                                    int newX = x + i * d;
                                    int newY = y + h * d;
                                    if (newX < 0 || newX >=n || newY < 0 || newY >= m) {
                                        break;
                                    }
                                    Position newPos = Position.of(newX, newY);
                                    boolean struckObstruction = false;

                                    if (bombsByPosition.get(newPos) != null) {
                                        for (Bomb bomb : bombsByPosition.get(newPos)) {
                                            if (!isDetonated.contains(bomb)) {
                                                isDetonated.add(bomb);
                                                toDetonateBombs.add(bomb);
                                                hasBombUntil[bomb.getPos().getX()][bomb.getPos().getY()] =
                                                        minDetonateTime - 1;
                                                struckObstruction = true;
                                                nextTurnBoard[newX][newY] = isEmpty;
                                            }
                                        }
                                    }

                                    if (curTurnBoard[newX][newY] == isBox) {
                                        nextTurnBoard[newX][newY] = isEmpty;
                                        hasBoxUntil[newX][newY] = minDetonateTime - 1;
                                        struckObstruction = true;
                                    }

                                    if (curTurnBoard[newX][newY] == isWall) {
                                        struckObstruction = true;
                                    }

                                    exploadedCells.add(newPos);

                                    if (struckObstruction) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                for (Position pos : exploadedCells) {
                    explosions.putIfAbsent(pos, new ArrayList<>());
                    explosions.get(pos).add(minDetonateTime);
                }
                curTurnBoard = nextTurnBoard.clone();
            }
        }

        public boolean isCellPassable(Position pos, int time) {
            int x = pos.getX();
            int y = pos.getY();
            return !(hasWall[x][y] || hasBombUntil[x][y] >= time || hasBoxUntil[x][y] >= time ||
                    (explosions.get(pos) != null && explosions.get(pos).contains(time)));
        }

        public boolean isCellVacantForBomb(Position pos, int time) {
            int x = pos.getX();
            int y = pos.getY();
            return !(hasWall[x][y] || hasBombUntil[x][y] >= time || hasBoxUntil[x][y] >= time);
        }

        public boolean isCellBox(Position pos, int time) {
            return hasBoxUntil[pos.getX()][pos.getY()] >= time;
        }

        public int nextExplosionInCell(Position pos, int time) {
            List<Integer> cellExplosions = explosions.get(pos);
            if (cellExplosions == null) {
                return -1;
            } else {
                for (Integer explosionTime : cellExplosions) {
                    if (explosionTime > time) {
                        return explosionTime;
                    }
                }
                return -1;
            }
        }
    }

    static final class Wall implements GameObject {

        private final Position pos;

        public Wall(Position pos) {
            this.pos = pos;
        }

        public Position getPos() {
            return pos;
        }

        @Override
        public String toString() {
            return "Wall{" + "pos=" + pos + '}';
        }
    }

    static final class Box implements GameObject {

        private final Position pos;

        public Box(Position pos) {
            this.pos = pos;
        }

        public Position getPos() {
            return pos;
        }

        @Override
        public String toString() {
            return "Box{" + "pos=" + pos + '}';
        }
    }

    static final class Bomb implements TimelineGameObject {

        private final int createTime;
        private final int countdown;
        private final int range;
        private final Position pos;

        public Bomb(int createTime, int countdown, int range, Position pos) {
            this.createTime = createTime;
            this.countdown = countdown;
            this.range = range;
            this.pos = pos;
        }

        public int getCountdown() {
            return countdown;
        }

        public int getRange() {
            return range;
        }

        public Position getPos() {
            return pos;
        }

        public int createTime() {
            return createTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Bomb bomb = (Bomb) o;
            return createTime == bomb.createTime && countdown == bomb.countdown && range == bomb.range &&
                    Objects.equals(pos, bomb.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createTime, countdown, range, pos);
        }

        @Override
        public String toString() {
            return "Bomb{" + "createTime=" + createTime + ", countdown=" + countdown + ", range=" + range + ", pos=" +
                    pos + '}';
        }
    }

    interface GameObject {

        Position getPos();
    }

    interface TimelineGameObject extends GameObject {

        int createTime();
    }

    static final class Position {

        private static final Position[][] store;

        static {
            store = new Position[MAXX][MAXY];
            for (int i = 0; i < MAXX; i++) {
                for (int h = 0; h < MAXY; h++) {
                    store[i][h] = new Position(i, h);
                }
            }
        }

        private final int x;
        private final int y;

        private Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public static Position of(int x, int y) {
            return store[x][y];
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Position position = (Position) o;
            return x == position.x && y == position.y;
        }

        @Override
        public int hashCode() {
            return x * MAXY + y;
        }

        @Override
        public String toString() {
            return "p{" + x + "," + y + '}';
        }
    }
}
