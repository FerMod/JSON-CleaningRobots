import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.Random;
import java.util.logging.Logger;

import java.util.concurrent.ThreadLocalRandom;

public class MarsEnv extends Environment {

    public static final int GSize = 7; // grid size
    public static final int GARB = 16; // garbage code in grid model

    /** Amount of garbage to add to the environment */
    public static final int GARB_AMOUNT = 5;

    enum SearchType {
        LEFT_RIGHT, TOP_DOWN, ZIG_ZAG_LEFT_RIGHT, ZIG_ZAG_TOP_DOWN
    }

    /** Type of search that will follow the agent */
    public static SearchType SEARCH_TYPE = SearchType.ZIG_ZAG_TOP_DOWN;

    public static final Term ns = Literal.parseLiteral("next(slot)");
    public static final Term pg = Literal.parseLiteral("pick(garb)");
    public static final Term dg = Literal.parseLiteral("drop(garb)");
    public static final Term bg = Literal.parseLiteral("burn(garb)");
    public static final Literal g1 = Literal.parseLiteral("garbage(r1)");
    public static final Literal g2 = Literal.parseLiteral("garbage(r2)");

    static Logger logger = Logger.getLogger(MarsEnv.class.getName());

    private MarsModel model;
    private MarsView view;

    @Override
    public void init(String[] args) {
        model = new MarsModel();
        view = new MarsView(model);
        model.setView(view);
        updatePercepts();
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        logger.info(ag + " doing: " + action);
        try {
            if (action.equals(ns)) {
                model.nextSlot();
            } else if (action.getFunctor().equals("move_towards")) {
                int x = (int) ((NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((NumberTerm) action.getTerm(1)).solve();
                model.moveTowards(0, x, y);
            } else if (action.equals(pg)) {
                model.pickGarb();
            } else if (action.equals(dg)) {
                model.dropGarb();
            } else if (action.equals(bg)) {
                model.burnGarb();
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        updatePercepts();

        try {
            Thread.sleep(200);
        } catch (Exception e) {
        }
        informAgsEnvironmentChanged();
        return true;
    }

    /** creates the agents perception based on the MarsModel */
    void updatePercepts() {
        clearPercepts();

        Location r1Loc = model.getAgPos(0);
        Location r2Loc = model.getAgPos(1);

        Literal pos1 = Literal.parseLiteral("pos(r1," + r1Loc.x + "," + r1Loc.y + ")");
        Literal pos2 = Literal.parseLiteral("pos(r2," + r2Loc.x + "," + r2Loc.y + ")");

        addPercept(pos1);
        addPercept(pos2);

        if (model.hasObject(GARB, r1Loc)) {
            addPercept(g1);
        }
        if (model.hasObject(GARB, r2Loc)) {
            addPercept(g2);
        }
    }

    class MarsModel extends GridWorldModel {

        public static final int MErr = 2; // max error in pick garb
        int nerr = 0; // number of tries of pick garb
        boolean r1HasGarb = false; // whether r1 is carrying garbage or not

        /** Number of tries of burning garb */
        int nBurnErr = 0;

        boolean reverse = false;

        Random random = new Random(System.currentTimeMillis());

        private MarsModel() {
            super(GSize, GSize, 2);

            // Initial location of agents
            try {

                // r1
                int x = generateRandom(0, GSize - 1);
                int y = generateRandom(0, GSize - 1);
                setAgPos(0, x, y);
                logger.info("r1(x: " + x + ", y: " + y + ")");

                // r2
                x = generateRandom(0, GSize - 1);
                y = generateRandom(0, GSize - 1);
                setAgPos(1, x, y);
                logger.info("r2(x: " + x + ", y: " + y + ")");

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Initial location of garbage
            initGarb(GARB_AMOUNT);

        }

        private void initGarb(int num) {

            int i = 0;
            while (i < num) {

                int x = generateRandom(0, GSize - 1);
                int y = generateRandom(0, GSize - 1);
                logger.info("x: " + x + ", y: " + y);
                logger.info("hasObject(GARB, x, y): " + hasObject(GARB, x, y));

                if (!hasObject(GARB, x, y)) {
                    add(GARB, x, y);
                    i++;
                }

            }

        }

        private int generateRandom(int min, int max) {
            return ThreadLocalRandom.current().nextInt(min, max + 1);
            // return random.nextInt(GSize); // Old way of generating random int
        }

        void nextSlot() throws Exception {

            switch (SEARCH_TYPE) {
            case TOP_DOWN:
                leftRightSearch(0);
                break;
            case LEFT_RIGHT:
                topDownSearch(0);
                break;
            case ZIG_ZAG_LEFT_RIGHT:
                zigZagLeftRightSearch(0);
                break;
            case ZIG_ZAG_TOP_DOWN:
            default:
                zigZagTopDownSearch(0);
                break;
            }

            setAgPos(1, getAgPos(1)); // just to draw it in the view

        }

        private void leftRightSearch(int ag) {

            Location pos = getAgPos(ag);
            pos.x++;

            if (pos.x == getWidth()) {
                pos.x = 0;
                pos.y++;
            }

            // Finished searching the whole grid
            if (pos.y == getHeight()) {
                pos.y = 0;
            }

            setAgPos(ag, pos);

        }

        private void topDownSearch(int ag) {

            Location pos = getAgPos(ag);
            pos.y++;

            if (pos.y == getHeight()) {
                pos.y = 0;
                pos.x++;
            }

            if (pos.x == getWidth()) {
                pos.x = 0;
            }

            setAgPos(ag, pos);

        }

        private void zigZagLeftRightSearch(int ag) {

            Location pos = getAgPos(ag);

            if (reverse) {
                // Check if it reached the extreme
                if (pos.x == 0) {
                    reverse = false;
                    pos.y++;
                } else {
                    pos.x--;
                }
            } else {
                // Check if it reached the extreme
                if (pos.x == getWidth() - 1) {
                    reverse = true;
                    pos.y++;
                } else {
                    pos.x++;
                }
            }

            if (pos.y == getHeight()) {
                pos.x = 0;
                pos.y = 0;
                reverse = false;
            }

            setAgPos(ag, pos);

        }

        private void zigZagTopDownSearch(int ag) {

            Location pos = getAgPos(ag);

            if (reverse) {
                // Check if it reached the extreme
                if (pos.y == 0) {
                    reverse = false;
                    pos.x++;
                } else {
                    pos.y--;
                }
            } else {
                // Check if it reached the extreme
                if (pos.y == getHeight() - 1) {
                    reverse = true;
                    pos.x++;
                } else {
                    pos.y++;
                }
            }

            if (pos.x == getHeight()) {
                pos.x = 0;
                pos.y = 0;
                reverse = false;
            }

            setAgPos(ag, pos);

        }

        void moveTowards(int ag, int x, int y) throws Exception {

            Location pos = getAgPos(ag);

            if (pos.x < x) {
                pos.x++;
            } else if (pos.x > x) {
                pos.x--;
            }

            if (pos.y < y) {
                pos.y++;
            } else if (pos.y > y) {
                pos.y--;
            }

            setAgPos(ag, pos);
            setAgPos(1, getAgPos(1)); // just to draw it in the view

        }

        void pickGarb() {
            // r1 location has garbage
            if (model.hasObject(GARB, getAgPos(0))) {
                // Sometimes the action doesnt work, but never more than MErr times
                if (random.nextBoolean() || nerr == MErr) {
                    remove(GARB, getAgPos(0));
                    nerr = 0;
                    r1HasGarb = true;
                } else {
                    nerr++;
                }
            }
        }

        void dropGarb() {
            if (r1HasGarb) {
                r1HasGarb = false;
                add(GARB, getAgPos(0));
            }
        }

        void burnGarb() {
            // r2 location has garbage
            if (model.hasObject(GARB, getAgPos(1))) {
                if (random.nextBoolean() || nBurnErr == MErr) {
                    remove(GARB, getAgPos(1));
                    nBurnErr = 0;
                    logger.info("r2 burn SUCCESS (nBurnErr: " + nBurnErr + ")");
                } else {
                    nBurnErr++;
                    logger.info("r2 burn FAILED (nBurnErr: " + nBurnErr + ")");
                }
            }
        }

    }

    class MarsView extends GridWorldView {

        public MarsView(MarsModel model) {
            super(model, "Mars World", 600);
            defaultFont = new Font("Arial", Font.BOLD, 18); // change default font
            setVisible(true);
            // repaint();
        }

        /** draw application objects */
        @Override
        public void draw(Graphics g, int x, int y, int object) {
            switch (object) {
            case MarsEnv.GARB:
                drawGarb(g, x, y);
                break;
            }
        }

        @Override
        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            String label = "R" + (id + 1);
            c = Color.blue;
            if (id == 0) {
                c = Color.yellow;
                if (((MarsModel) model).r1HasGarb) {
                    label += " - G";
                    c = Color.orange;
                }
            }
            super.drawAgent(g, x, y, c, -1);
            if (id == 0) {
                g.setColor(Color.black);
            } else {
                g.setColor(Color.white);
            }
            super.drawString(g, x, y, defaultFont, label);
            // repaint();
        }

        public void drawGarb(Graphics g, int x, int y) {
            super.drawObstacle(g, x, y);
            g.setColor(Color.white);
            drawString(g, x, y, defaultFont, "G");
        }

    }
}
