package yio.tro.antiyoy.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Manual verification harness: runs the real game, but takes its taps from a command file and
 * captures frames out of the GL framebuffer.
 * <p>
 * Both halves exist because of the environment, not by preference. Under WSLg java.awt.Robot
 * captures black frames and cannot synthesize input into the GLFW window, so the screen has to be
 * read from inside the GL context and the taps have to enter through YioGdxGame's own
 * InputProcessor methods - which is the same entry point GLFW uses, so the UI path under test is
 * the real one.
 * <p>
 * Not part of the game. Run with: ./gradlew :desktop:run -PmainClass=...VerifyHarness
 */
public class VerifyHarness extends YioGdxGame {

    static File cmdFile;
    static File ackFile;
    int consumedLines;
    int frames;
    int waitFrames;


    public static void main(String[] args) {
        cmdFile = new File(System.getProperty("harness.dir") + "/cmd.txt");
        ackFile = new File(System.getProperty("harness.dir") + "/ack.txt");

        YioGdxGame.platformType = PlatformType.pc;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Antiyoy verify");
        config.setWindowedMode(540, 960);
        config.useVsync(true);

        new Lwjgl3Application(new VerifyHarness(), config);
    }


    @Override
    public void render() {
        super.render();

        frames++;
        if (waitFrames > 0) {
            waitFrames--;
            return;
        }
        if (frames % 5 != 0) return;

        try {
            pumpCommands();
        } catch (Exception e) {
            ack("ERROR " + e);
        }
    }


    private void pumpCommands() throws Exception {
        if (!cmdFile.exists()) return;

        List<String> lines = Files.readAllLines(cmdFile.toPath(), Charset.forName("UTF-8"));
        if (lines.size() <= consumedLines) return;

        String line = lines.get(consumedLines).trim();
        consumedLines++;
        if (line.length() == 0 || line.startsWith("#")) return;

        String[] t = line.split("\\s+");
        String cmd = t[0];

        if (cmd.equals("wait")) {
            waitFrames = Integer.parseInt(t[1]);
            ack("waited " + t[1]);
        } else if (cmd.equals("tap")) {
            int x = Integer.parseInt(t[1]);
            int y = Integer.parseInt(t[2]);
            touchDown(x, y, 0, 0);
            touchUp(x, y, 0, 0);
            waitFrames = 20;
            ack("tap " + x + "," + y + " focused=" + gameController.fieldManager.focusedHex);
        } else if (cmd.equals("key")) {
            keyDown(Integer.parseInt(t[1]));
            waitFrames = 20;
            ack("key " + t[1]);
        } else if (cmd.equals("shot")) {
            shot(t[1]);
        } else if (cmd.equals("money")) {
            Province province = gameController.fieldManager.selectedProvince;
            if (province == null) {
                ack("money: no selected province");
            } else {
                province.money = Integer.parseInt(t[1]);
                ack("money set to " + province.money);
            }
        } else if (cmd.equals("info")) {
            ack(info());
        } else if (cmd.equals("hex")) {
            ack(hexInfo(Integer.parseInt(t[1]), Integer.parseInt(t[2])));
        } else if (cmd.equals("quit")) {
            ack("bye");
            Gdx.app.exit();
        } else {
            ack("unknown command: " + line);
        }
    }


    private void shot(String name) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        String path = System.getProperty("harness.dir") + "/" + name;
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap);
        pixmap.dispose();
        ack("shot " + path);
    }


    private String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("turn=").append(gameController.turn);
        sb.append(" tipType=").append(gameController.selectionManager.getTipType());
        Province province = gameController.fieldManager.selectedProvince;
        if (province == null) {
            sb.append(" selectedProvince=null");
            return sb.toString();
        }
        sb.append(" money=").append(province.money);
        sb.append(" hexes=").append(province.hexList.size());
        sb.append(" farms=").append(province.countObjects(Obj.FARM));
        sb.append(" farmPrice=").append(province.getCurrentFarmPrice());
        sb.append(" income=").append(province.getIncome());
        sb.append(" taxes=").append(province.getTaxes());
        sb.append(" profit=").append(province.getProfit());
        return sb.toString();
    }


    private String hexInfo(int i, int j) {
        Hex hex = gameController.fieldManager.field[i][j];
        StringBuilder sb = new StringBuilder();
        sb.append("hex(").append(i).append(",").append(j).append(")");
        sb.append(" active=").append(hex.active);
        sb.append(" fraction=").append(hex.fraction);
        sb.append(" obj=").append(hex.objectInside);
        sb.append(" inMoveZone=").append(hex.inMoveZone);
        if (hex.containsUnit()) {
            sb.append(" unit[s=").append(hex.unit.strength);
            sb.append(" ready=").append(hex.unit.isReadyToMove()).append("]");
        }
        int height = Gdx.graphics.getBackBufferHeight();
        sb.append(" screen=").append(Math.round(hex.pos.x)).append(",").append(Math.round(height - hex.pos.y));
        return sb.toString();
    }


    private void ack(String message) {
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(ackFile, true));
            writer.println("[" + frames + "] " + message);
            writer.close();
        } catch (Exception e) {
            System.out.println("ack failed: " + e);
        }
    }
}
