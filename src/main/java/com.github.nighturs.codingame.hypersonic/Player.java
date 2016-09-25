package com.github.nighturs.codingame.hypersonic;

import java.util.*;

class Player {

    static int MAXX = 11;
    static int MAXY = 13;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        int myId = in.nextInt();
        in.nextLine();

        while (true) {
            List<GameObject> gameObjects = new ArrayList<>();
            for (int i = 0; i < height; i++) {
                String row = in.nextLine();
                for (int h = 0; h < width; h++) {
                    if (row.charAt(h) == '0') {
                        gameObjects.add(new Box(Position.of(i, h)));
                    }
                }
            }
            int entities = in.nextInt();
            Bomberman myBomberman = null;
            List<Bomberman> enemyBomberman = new ArrayList<>();
            for (int i = 0; i < entities; i++) {
                int entityType = in.nextInt();
                int owner = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int param1 = in.nextInt();
                int param2 = in.nextInt();
                switch (entityType) {
                    case 0:
                        Bomberman b = new Bomberman(owner, Position.of(y, x), 3, 8, param1, 1);
                        if (owner == myId) {
                            myBomberman = b;
                        } else {
                            enemyBomberman.add(b);
                        }
                        break;
                    case 1:
                        gameObjects.add(new Bomb(0, param1, 3, Position.of(y, x), owner));
                        break;
                }
            }
            in.nextLine();

            GameState gs = new GameState(height, width, myBomberman, enemyBomberman, gameObjects);

            System.out.println(FarmBoxesStrategy.createStrategy(gs).action().formatLine());
        }
    }

    static class GameState {

        private final int n;
        private final int m;
        private final Bomberman myBomberman;
        private final List<Bomberman> enemyBomberman;
        private final Board board;

        public GameState(int n,
                         int m,
                         Bomberman myBomberman,
                         List<Bomberman> enemyBomberman,
                         List<GameObject> gameObjects) {
            this.n = n;
            this.m = m;
            this.myBomberman = myBomberman;
            this.enemyBomberman = enemyBomberman;
            this.board = Board.createBoard(n, m, gameObjects);
        }

        public int getN() {
            return n;
        }

        public int getM() {
            return m;
        }

        public Bomberman getMyBomberman() {
            return myBomberman;
        }

        public List<Bomberman> getEnemyBomberman() {
            return enemyBomberman;
        }

        public Board getBoard() {
            return board;
        }
    }

    static class FarmBoxesStrategy implements Strategy {

        private static final int TURNS_TO_REACH_SAFETY = 10;
        private static final int MAX_POSITION_VISIT_TIMES = 5;
        private final Action action;

        public static FarmBoxesStrategy createStrategy(GameState gameState) {
            return new FarmBoxesStrategy(searchForBestBomb(gameState,
                    gameState.getMyBomberman().getPos(),
                    0,
                    gameState.getBoard()));
        }

        private FarmBoxesStrategy(Action action) {
            this.action = action;
        }

        private static Action searchForBestBomb(GameState gameState, Position myPosition, int time, Board board) {
            int myBombermanId = gameState.getMyBomberman().getId();

            Queue<SearchPosition> queue = new ArrayDeque<>();
            queue.add(new SearchPosition(null, myPosition, time));

            double bestScore = Double.MIN_VALUE;
            Action bestScoreAction = null;
            Map<Position, Set<Integer>> positionVisitTimes = new HashMap<>();
            positionVisitTimes.put(myPosition, new HashSet<>());
            positionVisitTimes.get(myPosition).add(time);

            while (!queue.isEmpty()) {
                SearchPosition sp = queue.poll();
                int nowX = sp.getPos().getX();
                int nowY = sp.getPos().getY();
                int nowTime = sp.getTime();
                int nextTime = nowTime + 1;
                int leftBombs = gameState.getMyBomberman().getOverallBombs() -
                        board.bombermanBombsUsed(myBombermanId, nextTime);

                if (leftBombs > 0 && board.isCellVacantForBomb(Position.of(nowX, nowY), nextTime)) {
                    int explosionTime = nextTime + gameState.getMyBomberman().getBombCountdown();
                    int nextExplosion = board.nextExplosionInCell(sp.getPos(), nowTime);
                    if (nextExplosion != -1) {
                        explosionTime = Math.min(explosionTime, nextExplosion);
                    }
                    int boxesBlown = 0;

                    for (int i = -1; i <= 1; i++) {
                        for (int h = -1; h <= 1; h++) {
                            if (i == 0 ^ h == 0) {
                                for (int d = 0; d < gameState.getMyBomberman().getBombRange(); d++) {
                                    int newX = nowX + i * d;
                                    int newY = nowY + h * d;
                                    if (!Position.isValid(gameState.getN(), gameState.getM(), newX, newY)) {
                                        break;
                                    }
                                    Position newPos = Position.of(newX, newY);
                                    if (board.isCellBox(newPos, explosionTime)) {
                                        boxesBlown++;
                                    }
                                    if (!board.isCellPassable(newPos, explosionTime)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    MoveAction moveToSurvive = findMoveToSurvive(gameState,
                            sp.getPos(),
                            nowTime,
                            Board.appendTimeline(board,
                                    Collections.singletonList(new Bomb(nextTime,
                                            gameState.getMyBomberman().getBombCountdown(),
                                            gameState.getMyBomberman().getBombRange(),
                                            sp.getPos(),
                                            myBombermanId))));
                    if (moveToSurvive != null) {
                        double curScore = calcScore(explosionTime, boxesBlown);
                        if (bestScore < curScore) {
                            bestScore = curScore;
                            if (sp.getInitiateAction() != null) {
                                MoveAction action = (MoveAction) sp.getInitiateAction();
                                bestScoreAction = new MoveAction(action.getPos(),
                                        String.format("BOMB{%d,%d}", sp.getPos().getX(), sp.getPos().getY()));
                            } else {
                                bestScoreAction = new PlaceBombAction(moveToSurvive.getPos());
                            }
                        }
                    }
                }

                for (int j1 = -1; j1 <= 1; j1++) {
                    for (int j2 = -1; j2 <= 1; j2++) {
                        if (j1 == 0 || j2 == 0) {
                            int newX = nowX + j1;
                            int newY = nowY + j2;
                            if (!Position.isValid(gameState.getN(), gameState.getM(), newX, newY)) {
                                continue;
                            }
                            Position newPos = Position.of(newX, newY);
                            if (positionVisitTimes.containsKey(newPos) &&
                                    (positionVisitTimes.get(newPos).contains(nextTime) ||
                                            positionVisitTimes.get(newPos).size() >= MAX_POSITION_VISIT_TIMES)) {
                                continue;
                            }
                            if (board.isCellPassable(newPos, nextTime)) {
                                positionVisitTimes.putIfAbsent(newPos, new HashSet<>());
                                positionVisitTimes.get(newPos).add(nextTime);
                                Action initiateAction = sp.getInitiateAction() == null ?
                                        new MoveAction(newPos) :
                                        sp.getInitiateAction();
                                queue.add(new SearchPosition(initiateAction, newPos, nextTime));
                            }
                        }
                    }
                }
            }

            return bestScoreAction == null ? new MoveAction(myPosition) : bestScoreAction;
        }

        private static MoveAction findMoveToSurvive(GameState gameState, Position myPosition, int time, Board board) {
            Queue<SearchPosition> queue = new ArrayDeque<>();
            queue.add(new SearchPosition(null, myPosition, time));

            Map<Position, Set<Integer>> positionVisitTimes = new HashMap<>();
            positionVisitTimes.put(myPosition, new HashSet<>());
            positionVisitTimes.get(myPosition).add(time);
            MoveAction moveToSurvive = null;

            while (!queue.isEmpty()) {
                SearchPosition sp = queue.poll();
                int nowX = sp.getPos().getX();
                int nowY = sp.getPos().getY();
                int nowTime = sp.getTime();
                int nextTime = nowTime + 1;

                if (sp.getTime() - time >= TURNS_TO_REACH_SAFETY) {
                    moveToSurvive = (MoveAction) sp.getInitiateAction();
                    break;
                }

                for (int j1 = -1; j1 <= 1; j1++) {
                    for (int j2 = -1; j2 <= 1; j2++) {
                        if (j1 == 0 || j2 == 0) {
                            int newX = nowX + j1;
                            int newY = nowY + j2;
                            if (!Position.isValid(gameState.getN(), gameState.getM(), newX, newY)) {
                                continue;
                            }
                            Position newPos = Position.of(newX, newY);
                            if (positionVisitTimes.containsKey(newPos) &&
                                    (positionVisitTimes.get(newPos).contains(nextTime) ||
                                            positionVisitTimes.get(newPos).size() >= MAX_POSITION_VISIT_TIMES)) {
                                continue;
                            }

                            if (board.isCellPassable(newPos, nextTime)) {
                                positionVisitTimes.putIfAbsent(newPos, new HashSet<>());
                                positionVisitTimes.get(newPos).add(nextTime);
                                Action initiateAction = sp.getInitiateAction() == null ?
                                        new MoveAction(newPos) :
                                        sp.getInitiateAction();
                                queue.add(new SearchPosition(initiateAction, newPos, nextTime));
                            }
                        }
                    }
                }
            }
            return moveToSurvive;
        }

        private static double calcScore(int time, int boxes) {
            if (boxes > 0) {
                return 1.0 / time;
            } else {
                return 0;
            }
        }

        @Override
        public Action action() {
            return action;
        }

        @Override
        public int priority() {
            return 1;
        }

        private static class SearchPosition {

            private final Action initiateAction;
            private final Position pos;
            private final int time;

            public SearchPosition(Action initiateAction, Position pos, int time) {
                this.initiateAction = initiateAction;
                this.pos = pos;
                this.time = time;
            }

            public Action getInitiateAction() {
                return initiateAction;
            }

            public Position getPos() {
                return pos;
            }

            public int getTime() {
                return time;
            }
        }
    }

    interface Strategy {

        Action action();

        int priority();
    }

    interface Action {

        String formatLine();
    }

    static class MoveAction implements Action {

        private final Position pos;
        private final String text;

        public MoveAction(Position pos) {
            this(pos, "");
        }

        public MoveAction(Position pos, String text) {
            this.pos = pos;
            this.text = text;
        }

        public Position getPos() {
            return pos;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MoveAction that = (MoveAction) o;
            return Objects.equals(pos, that.pos) && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, text);
        }

        @Override
        public String formatLine() {
            return String.format("MOVE %d %d %s", pos.getY(), pos.getX(), text);
        }

        @Override
        public String toString() {
            return formatLine();
        }
    }

    static class PlaceBombAction implements Action {

        private final Position pos;
        private final String text;

        public PlaceBombAction(Position pos) {
            this(pos, "");
        }

        public PlaceBombAction(Position pos, String text) {
            this.pos = pos;
            this.text = text;
        }

        public Position getPos() {
            return pos;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PlaceBombAction that = (PlaceBombAction) o;
            return Objects.equals(pos, that.pos) && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, text);
        }

        @Override
        public String formatLine() {
            return String.format("BOMB %d %d %s", pos.getY(), pos.getX(), text);
        }

        @Override
        public String toString() {
            return formatLine();
        }
    }

    static class Board {

        private static final int INF = Integer.MAX_VALUE;
        private static final int MAX_BOMBERMAN = 4;
        final int n;
        final int m;
        final List<GameObject> gameObjects;
        final boolean[][] hasWall;
        final int[][] hasBoxUntil;
        final int[][] hasBombUntil;
        final Map<Position, List<Integer>> explosions;
        final int[] bombsByBomberman;
        final List<List<Integer>> explosionTimesByBomberman;

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
            this.bombsByBomberman = new int[MAX_BOMBERMAN];
            this.explosionTimesByBomberman = new ArrayList<>();
            for (int i = 0; i < MAX_BOMBERMAN; i++) {
                explosionTimesByBomberman.add(new ArrayList<>());
            }
            this.explosions = new HashMap<>();
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
                    Bomb bomb = (Bomb) o;
                    bombs.add(bomb);
                    bombsByPosition.putIfAbsent(o.getPos(), new ArrayList<>());
                    bombsByPosition.get(o.getPos()).add(bomb);
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isBomb;
                    bombsByBomberman[bomb.getOwnerId()]++;
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
                explosionTimesByBomberman.get(initiatorBomb.getOwnerId()).add(minDetonateTime);
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
                                    if (!Position.isValid(n, m, newX, newY)) {
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
                                                explosionTimesByBomberman.get(bomb.getOwnerId()).add(minDetonateTime);
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

        public int bombermanBombsUsed(int bombermanId, int time) {
            int usedOverall = bombsByBomberman[bombermanId];
            for (int i = 0; i < explosionTimesByBomberman.get(bombermanId).size(); i++) {
                int explosionTime = explosionTimesByBomberman.get(bombermanId).get(i);
                if (explosionTime < time) {
                    usedOverall--;
                }
            }
            return usedOverall;
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
        private final int ownerId;

        public Bomb(int createTime, int countdown, int range, Position pos, int ownerId) {
            this.createTime = createTime;
            this.countdown = countdown;
            this.range = range;
            this.pos = pos;
            this.ownerId = ownerId;
        }

        public int getCountdown() {
            return countdown;
        }

        public int getRange() {
            return range;
        }

        @Override
        public Position getPos() {
            return pos;
        }

        @Override
        public int createTime() {
            return createTime;
        }

        public int getOwnerId() {
            return ownerId;
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
                    ownerId == bomb.ownerId && Objects.equals(pos, bomb.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createTime, countdown, range, pos, ownerId);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Bomb{");
            sb.append("createTime=").append(createTime);
            sb.append(", countdown=").append(countdown);
            sb.append(", range=").append(range);
            sb.append(", pos=").append(pos);
            sb.append(", ownerId=").append(ownerId);
            sb.append('}');
            return sb.toString();
        }
    }

    static final class Bomberman implements GameObject {

        private final int id;
        private final Position pos;
        private final int bombRange;
        private final int bombCountdown;
        private final int leftBombs;
        private final int overallBombs;

        public Bomberman(int id, Position pos, int bombRange, int bombCountdown, int leftBombs, int overallBombs) {
            this.id = id;
            this.pos = pos;
            this.bombRange = bombRange;
            this.bombCountdown = bombCountdown;
            this.leftBombs = leftBombs;
            this.overallBombs = overallBombs;
        }

        public int getId() {
            return id;
        }

        @Override
        public Position getPos() {
            return pos;
        }

        public int getBombRange() {
            return bombRange;
        }

        public int getBombCountdown() {
            return bombCountdown;
        }

        public int getLeftBombs() {
            return leftBombs;
        }

        public int getOverallBombs() {
            return overallBombs;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Bomberman{");
            sb.append("id=").append(id);
            sb.append(", pos=").append(pos);
            sb.append(", bombRange=").append(bombRange);
            sb.append(", bombCountdown=").append(bombCountdown);
            sb.append(", leftBombs=").append(leftBombs);
            sb.append(", overallBombs=").append(overallBombs);
            sb.append('}');
            return sb.toString();
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

        public static boolean isValid(int n, int m, int x, int y) {
            return !(x < 0 || x >= n || y < 0 || y >= m);
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
