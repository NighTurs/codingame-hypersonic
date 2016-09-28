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
                        gameObjects.add(new Box(Position.of(i, h), Box.Type.EMPTY));
                    } else if (row.charAt(h) == '1') {
                        gameObjects.add(new Box(Position.of(i, h), Box.Type.RANGE_ITEM));
                    } else if (row.charAt(h) == '2') {
                        gameObjects.add(new Box(Position.of(i, h), Box.Type.BOMB_ITEM));
                    } else if (row.charAt(h) == 'X') {
                        gameObjects.add(new Wall(Position.of(i, h)));
                    }
                }
            }
            int entities = in.nextInt();
            List<Bomberman> incompleteBombermans = new ArrayList<>();
            Map<Integer, Integer> bombsByBomberman = new HashMap<>();
            for (int i = 0; i < entities; i++) {
                int entityType = in.nextInt();
                int owner = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int param1 = in.nextInt();
                int param2 = in.nextInt();
                switch (entityType) {
                    case 0:
                        incompleteBombermans.add(new Bomberman(owner, Position.of(y, x), param2, 8, param1, 0));
                        break;
                    case 1:
                        gameObjects.add(new Bomb(0, param1, param2, Position.of(y, x), owner));
                        bombsByBomberman.putIfAbsent(owner, 0);
                        bombsByBomberman.put(owner, bombsByBomberman.get(owner) + 1);
                        break;
                    case 2:
                        gameObjects.add(new Item(param1 == 1 ? Item.Type.RANGE : Item.Type.BOMB, Position.of(y, x)));
                        break;

                }
            }
            Bomberman myBomberman = null;
            List<Bomberman> enemyBomberman = new ArrayList<>();
            for (Bomberman b : incompleteBombermans) {
                bombsByBomberman.putIfAbsent(b.getId(), 0);
                Bomberman completeBomberman = new Bomberman(b.getId(),
                        b.getPos(),
                        b.getBombRange(),
                        b.getBombCountdown(),
                        b.getLeftBombs(),
                        b.getLeftBombs() + bombsByBomberman.get(b.getId()));
                if (completeBomberman.getId() == myId) {
                    myBomberman = completeBomberman;
                } else {
                    enemyBomberman.add(completeBomberman);
                }
            }

            in.nextLine();

            GameState gs = new GameState(height, width, myBomberman, enemyBomberman, gameObjects);

            System.out.println(planTurn(gs).formatLine());
        }
    }

    static Action planTurn(GameState gameState) {
        List<Strategy> strategies = new ArrayList<>();
        strategies.add(FarmBoxesStrategy.createStrategy(gameState));
        strategies.add(SurviveStrategy.createStrategy(gameState));
        strategies.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        strategies.add(DontAllowToBeTrappedStrategy.createStrategy(gameState, strategies.get(0).action()));
        strategies.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return Optional.ofNullable(strategies.get(0).action())
                .orElse(new MoveAction(gameState.getMyBomberman().getPos()));
    }

    static class GameState {

        private final int n;
        private final int m;
        private final Bomberman myBomberman;
        private final List<Bomberman> enemyBomberman;
        private final Board board;
        private final Box[][] boxes;
        private final Item[][] items;

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
            boxes = new Box[n][m];
            items = new Item[n][m];
            for (GameObject o : gameObjects) {
                if (o instanceof Box) {
                    Box box = (Box) o;
                    boxes[box.getPos().getX()][box.getPos().getY()] = box;
                    if (box.getType() == Box.Type.BOMB_ITEM) {
                        items[box.getPos().getX()][box.getPos().getY()] = new Item(Item.Type.BOMB, box.getPos());
                    } else if (box.getType() == Box.Type.RANGE_ITEM) {
                        items[box.getPos().getX()][box.getPos().getY()] = new Item(Item.Type.RANGE, box.getPos());
                    }
                } else if (o instanceof Item) {
                    items[o.getPos().getX()][o.getPos().getY()] = (Item) o;
                }
            }
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

        public Box getBox(int x, int y) {
            return boxes[x][y];
        }

        public Item getItem(int x, int y) {
            return items[x][y];
        }
    }

    static class DontAllowToBeTrappedStrategy implements Strategy {

        private final Action action;
        private final int priority;

        public static DontAllowToBeTrappedStrategy createStrategy(GameState gameState, Action myMove) {
            if (myMove == null) {
                return new DontAllowToBeTrappedStrategy(null, -10);
            }
            Action action = pickOtherActionIfInHazzard(gameState, myMove);
            if (action == null) {
                return new DontAllowToBeTrappedStrategy(null, -10);
            } else {
                return new DontAllowToBeTrappedStrategy(action, 100);
            }
        }

        public DontAllowToBeTrappedStrategy(Action action, int priority) {
            this.action = action;
            this.priority = priority;
        }

        private static Action pickOtherActionIfInHazzard(GameState gameState, Action myMove) {
            Bomberman myBomberman = gameState.getMyBomberman();
            for (Bomberman enemy : gameState.getEnemyBomberman()) {
                if (enemy.getLeftBombs() == 0) {
                    continue;
                }
                List<TimelineGameObject> newBombs = new ArrayList<>();
                newBombs.add(new Bomb(1,
                        enemy.getBombCountdown(),
                        enemy.getBombRange(),
                        enemy.getPos(),
                        enemy.getId()));
                if (myMove instanceof PlaceBombAction) {
                    newBombs.add(new Bomb(1,
                            myBomberman.getBombCountdown(),
                            myBomberman.getBombRange(),
                            myBomberman.getPos(),
                            myBomberman.getId()));
                }
                @SuppressWarnings("ConstantConditions")
                Position movingTo = myMove instanceof MoveAction ? ((MoveAction) myMove).getPos() :
                        ((PlaceBombAction) myMove).getPos();

                Board.appendTimeline(gameState.getBoard(), newBombs);
                MoveAction surviveAction = SurviveStrategy.findMoveToSurvive(gameState,
                        movingTo,
                        1,
                        Board.appendTimeline(gameState.getBoard(), newBombs));

                if (surviveAction == null) {
                    return SurviveStrategy.findMoveToSurvive(gameState,
                            myBomberman.getPos(),
                            0,
                            Board.appendTimeline(gameState.getBoard(), Collections.singletonList(newBombs.get(0))));
                }
            }
            return null;
        }

        @Override
        public Action action() {
            return action;
        }

        @Override
        public int priority() {
            return priority;
        }
    }

    static class SurviveStrategy implements Strategy {
        private static final int TURNS_TO_REACH_SAFETY = 10;
        private static final int MAX_POSITION_VISIT_TIMES = 10;

        private final Action action;

        public static SurviveStrategy createStrategy(GameState gameState) {
            return new SurviveStrategy(findMoveToSurvive(gameState,
                    gameState.getMyBomberman().getPos(),
                    0,
                    gameState.getBoard()));
        }

        private SurviveStrategy(Action action) {
            this.action = action;
        }

        private static MoveAction findMoveToSurvive(GameState gameState, Position myPosition, int time, Board board) {
            Queue<FarmBoxesStrategy.SearchPosition> queue = new ArrayDeque<>();
            queue.add(new FarmBoxesStrategy.SearchPosition(null, myPosition, time, 0, 0, 0));

            Map<Position, Set<Integer>> positionVisitTimes = new HashMap<>();
            positionVisitTimes.put(myPosition, new HashSet<>());
            positionVisitTimes.get(myPosition).add(time);
            MoveAction moveToSurvive = null;

            while (!queue.isEmpty()) {
                FarmBoxesStrategy.SearchPosition sp = queue.poll();
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
                                queue.add(new FarmBoxesStrategy.SearchPosition(initiateAction, newPos, nextTime, 0, 0, 0));
                            }
                        }
                    }
                }
            }
            return moveToSurvive;
        }

        @Override
        public Action action() {
            return action;
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    static class FarmBoxesStrategy implements Strategy {

        private static final int MAX_POSITION_VISIT_TIMES = 5;
        private final Action action;
        private final int priority;

        public static FarmBoxesStrategy createStrategy(GameState gameState) {
            Action action = searchForBestBomb(gameState,
                    gameState.getMyBomberman().getPos(),
                    0,
                    gameState.getBoard());
            int priority = 20;
            if (action == null) {
                priority = 5;
            }
            return new FarmBoxesStrategy(action, priority);
        }

        private FarmBoxesStrategy(Action action, int priority) {
            this.action = action;
            this.priority = priority;
        }

        private static Action searchForBestBomb(GameState gameState, Position myPosition, int time, Board board) {
            int myBombermanId = gameState.getMyBomberman().getId();

            Queue<SearchPosition> queue = new PriorityQueue<>((SearchPosition a, SearchPosition b) -> {
                int timeCmp = Integer.compare(a.getTime(), b.getTime());
                if (timeCmp == 0) {
                    return Integer.compare(b.getPickedUpItems(), a.getPickedUpItems());
                } else {
                    return timeCmp;
                }
            });
            queue.add(new SearchPosition(null, myPosition, time, 0, 0, 0));

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
                int itemsPickedUp = sp.getPickedUpItems();

                if (leftBombs > 0 && board.isCellVacantForBomb(Position.of(nowX, nowY), nextTime)) {
                    int explosionTime = nextTime + gameState.getMyBomberman().getBombCountdown();
                    int nextExplosion = board.nextExplosionInCell(sp.getPos(), nowTime);
                    if (nextExplosion != -1) {
                        explosionTime = Math.min(explosionTime, nextExplosion);
                    }
                    int ordinaryBoxesBlown = 0;
                    int bombBoxesBlown = 0;

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
                                        Box box = gameState.getBox(newX, newY);
                                        if (box.getType() == Box.Type.BOMB_ITEM) {
                                            bombBoxesBlown++;
                                        } else {
                                            ordinaryBoxesBlown++;
                                        }
                                    }
                                    if (!board.isCellPassableByExplosion(newPos, explosionTime)) {
                                        break;
                                    }
                                    if (d != 0 && board.isCellHasItem(newPos, explosionTime)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    double curScore = calcScore(explosionTime,
                            gameState.getMyBomberman().getLeftBombs(),
                            ordinaryBoxesBlown,
                            bombBoxesBlown,
                            sp.getBombsPickedUp(),
                            sp.getRangePickedUp());

                    if (bestScore < curScore) {
                        MoveAction moveToSurvive = SurviveStrategy.findMoveToSurvive(gameState,
                                sp.getPos(),
                                nowTime,
                                Board.appendTimeline(board,
                                        Collections.singletonList(new Bomb(nextTime,
                                                gameState.getMyBomberman().getBombCountdown(),
                                                gameState.getMyBomberman().getBombRange(),
                                                sp.getPos(),
                                                myBombermanId))));
                        if (moveToSurvive != null) {
                            bestScore = curScore;
                            System.err.println(String.format(
                                    "%s, sc=%s, expTime=%s, leftB=%s, ordBo=%s, bBo=%s, bPicked=%s, rPicked=%s",
                                    sp.getPos(),
                                    bestScore,
                                    explosionTime,
                                    gameState.getMyBomberman().getLeftBombs(),
                                    ordinaryBoxesBlown,
                                    bombBoxesBlown,
                                    sp.getBombsPickedUp(),
                                    sp.getRangePickedUp()));
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
                                int newItemsPickedUp = itemsPickedUp;
                                int bombItemsPickedUp = sp.getBombsPickedUp();
                                int rangeItemsPickedUp = sp.getRangePickedUp();
                                if (board.isCellHasItem(newPos, nextTime)) {
                                    int minVisitTime =
                                            positionVisitTimes.get(newPos).stream().min(Integer::compare).get();
                                    if (minVisitTime >= nextTime) {
                                        if (gameState.getItem(newX, newY).getType() == Item.Type.BOMB) {
                                            bombItemsPickedUp++;
                                        } else {
                                            rangeItemsPickedUp++;
                                        }
                                    }
                                    newItemsPickedUp++;
                                }
                                queue.add(new SearchPosition(initiateAction,
                                        newPos,
                                        nextTime,
                                        newItemsPickedUp,
                                        bombItemsPickedUp,
                                        rangeItemsPickedUp));
                            }
                        }
                    }
                }
            }

            return bestScoreAction;
        }

        private static double calcScore(int time,
                                        int initialLeftBombs,
                                        int ordinaryBoxes,
                                        int bombBoxes,
                                        int bombsPickedUp,
                                        int rangePickedUp) {
            double score = 1000 - time;
            score += (initialLeftBombs < 2) ? ordinaryBoxes * 1.5 : ordinaryBoxes;
            score += (initialLeftBombs < 2) ? bombBoxes * 2 : bombBoxes * 1.5;
            score += bombsPickedUp * 1.5;
            score += rangePickedUp * 0.5;
            return score;
        }

        @Override
        public Action action() {
            return action;
        }

        @Override
        public int priority() {
            return priority;
        }

        static class SearchPosition {

            private final Action initiateAction;
            private final Position pos;
            private final int time;
            private final int pickedUpItems;
            private final int bombsPickedUp;
            private final int rangePickedUp;

            public SearchPosition(Action initiateAction,
                                  Position pos,
                                  int time,
                                  int pickedUpItems,
                                  int bombsPickedUp,
                                  int rangePickedUp) {
                this.initiateAction = initiateAction;
                this.pos = pos;
                this.time = time;
                this.pickedUpItems = pickedUpItems;
                this.bombsPickedUp = bombsPickedUp;
                this.rangePickedUp = rangePickedUp;
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

            public int getPickedUpItems() {
                return pickedUpItems;
            }

            public int getBombsPickedUp() {
                return bombsPickedUp;
            }

            public int getRangePickedUp() {
                return rangePickedUp;
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
        final int[][] hasBombSince;
        final int[][] hasBombUntil;
        final int[][] hasItemSince;
        final int[][] hasItemUntil;
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
            this.hasBombSince = fill(new int[n][m], INF);
            this.hasBombUntil = fill(new int[n][m], -1);
            this.hasItemSince = fill(new int[n][m], INF);
            this.hasItemUntil = fill(new int[n][m], -1);
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

        private static void arrayCopy(int[][] aSource, int[][] aDestination) {
            for (int i = 0; i < aSource.length; i++) {
                System.arraycopy(aSource[i], 0, aDestination[i], 0, aSource[i].length);
            }
        }

        private void calcFuture() {
            final int isEmpty = 0;
            final int isWall = 1;
            final int isBox = 2;
            final int isBomb = 3;
            final int isItem = 4;
            final int isBoxWithItem = 5;
            int[][] curTurnBoard = new int[n][m];
            int[][] nextTurnBoard = new int[n][m];
            List<Bomb> bombs = new ArrayList<>();
            Map<Position, List<Bomb>> bombsByPosition = new HashMap<>();
            for (GameObject o : gameObjects) {
                if (o instanceof Wall) {
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isWall;
                } else if (o instanceof Box) {
                    Box box = (Box) o;
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] =
                            box.getType() == Box.Type.EMPTY ? isBox : isBoxWithItem;
                    hasBoxUntil[o.getPos().getX()][o.getPos().getY()] = INF;
                } else if (o instanceof Bomb) {
                    Bomb bomb = (Bomb) o;
                    bombs.add(bomb);
                    bombsByPosition.putIfAbsent(o.getPos(), new ArrayList<>());
                    bombsByPosition.get(o.getPos()).add(bomb);
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isBomb;
                    bombsByBomberman[bomb.getOwnerId()]++;
                    hasBombSince[o.getPos().getX()][o.getPos().getY()] = bomb.createTime() + 1;
                } else if (o instanceof Item) {
                    curTurnBoard[o.getPos().getX()][o.getPos().getY()] = isItem;
                    hasItemUntil[o.getPos().getX()][o.getPos().getY()] = INF;
                    hasItemSince[o.getPos().getX()][o.getPos().getY()] = -1;
                }
            }
            arrayCopy(curTurnBoard, nextTurnBoard);
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
                                boolean useHackedTransparentItems = false;
                                for (int d = 0; d < range; d++) {
                                    int newX = x + i * d;
                                    int newY = y + h * d;
                                    if (!Position.isValid(n, m, newX, newY)) {
                                        break;
                                    }
                                    Position newPos = Position.of(newX, newY);
                                    boolean struckObstruction = false;

                                    if (bombsByPosition.get(newPos) != null && !useHackedTransparentItems) {
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

                                    if (curTurnBoard[newX][newY] == isBoxWithItem && !useHackedTransparentItems) {
                                        nextTurnBoard[newX][newY] = isItem;
                                        hasBoxUntil[newX][newY] = minDetonateTime - 1;
                                        hasItemSince[newX][newY] = minDetonateTime;
                                        hasItemUntil[newX][newY] = INF;
                                        struckObstruction = true;
                                    }

                                    if (curTurnBoard[newX][newY] == isBox && !useHackedTransparentItems) {
                                        nextTurnBoard[newX][newY] = isEmpty;
                                        hasBoxUntil[newX][newY] = minDetonateTime - 1;
                                        struckObstruction = true;
                                    }

                                    if (curTurnBoard[newX][newY] == isItem && !useHackedTransparentItems) {
                                        nextTurnBoard[newX][newY] = isEmpty;
                                        hasItemUntil[newX][newY] = minDetonateTime - 1;
                                        struckObstruction = false;
                                        useHackedTransparentItems = true;
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
                arrayCopy(nextTurnBoard, curTurnBoard);
            }
        }

        public boolean isCellPassableByExplosion(Position pos, int time) {
            int x = pos.getX();
            int y = pos.getY();
            return !(hasWall[x][y] || (hasBombUntil[x][y] >= time && hasBombSince[x][y] <= time) ||
                    hasBoxUntil[x][y] >= time);
        }

        public boolean isCellPassable(Position pos, int time) {
            int x = pos.getX();
            int y = pos.getY();
            return !(hasWall[x][y] || (hasBombUntil[x][y] >= time && hasBombSince[x][y] <= time) ||
                    hasBoxUntil[x][y] >= time ||
                    (explosions.get(pos) != null && explosions.get(pos).contains(time + 1)));
        }

        public boolean isCellVacantForBomb(Position pos, int time) {
            int x = pos.getX();
            int y = pos.getY();
            return !(hasWall[x][y] || (hasBombUntil[x][y] >= time && hasBombSince[x][y] <= time)
                    || hasBoxUntil[x][y] >= time);
        }

        public boolean isCellBox(Position pos, int time) {
            return hasBoxUntil[pos.getX()][pos.getY()] >= time;
        }

        public boolean isCellHasItem(Position pos, int time) {
            return hasItemSince[pos.getX()][pos.getY()] <= time && hasItemUntil[pos.getX()][pos.getY()] >= time;
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
        private final Type type;

        public Box(Position pos) {
            this(pos, Type.EMPTY);
        }

        public Box(Position pos, Type type) {
            this.pos = pos;
            this.type = type;
        }

        public Position getPos() {
            return pos;
        }

        public Type getType() {
            return type;
        }

        @Override
        public String toString() {
            return "Box{" + "pos=" + pos + '}';
        }

        enum Type {
            EMPTY,
            RANGE_ITEM,
            BOMB_ITEM
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

    static final class Item implements GameObject {
        private final Type type;
        private final Position pos;

        public Item(Type type, Position pos) {
            this.type = type;
            this.pos = pos;
        }

        public Type getType() {
            return type;
        }

        @Override
        public Position getPos() {
            return pos;
        }

        enum Type {
            RANGE,
            BOMB
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
