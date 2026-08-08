import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Rebuilds atlas_texture.png and the _low/_lowest pngs from the full-size pngs.
 *
 * Usage:
 *   javac tools/RebuildAtlas.java -d /tmp/rebuild-atlas
 *   java -Djava.awt.headless=true -cp /tmp/rebuild-atlas RebuildAtlas assets/field_elements
 *
 * Workflow: manually edit the full-size png (e.g. archer_tower.png), then run this.
 * It reads atlas_structure.txt, regenerates every _low/_lowest png by downscaling
 * its full-size counterpart to the size recorded in the structure file, and
 * repaints the whole atlas texture at the recorded coordinates.
 */
public class RebuildAtlas {

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        File structureFile = new File(dir, "atlas_structure.txt");
        if (!structureFile.exists()) {
            System.err.println("No atlas_structure.txt in " + dir.getAbsolutePath());
            System.exit(1);
        }

        // entry: name.png#x y w h
        List<String[]> entries = new ArrayList<>();
        int maxX = 0, maxY = 0;
        for (String line : java.nio.file.Files.readAllLines(structureFile.toPath())) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("#")) continue;
            String[] parts = line.split("#");
            String[] nums = parts[1].trim().split(" ");
            entries.add(new String[]{parts[0], nums[0], nums[1], nums[2], nums[3]});
            maxX = Math.max(maxX, Integer.parseInt(nums[0]) + Integer.parseInt(nums[2]));
            maxY = Math.max(maxY, Integer.parseInt(nums[1]) + Integer.parseInt(nums[3]));
        }

        // keep existing atlas dimensions if possible (game maps UVs from structure file, so exact size isn't critical, but stay consistent)
        File atlasFile = new File(dir, "atlas_texture.png");
        int atlasW = maxX, atlasH = maxY;
        if (atlasFile.exists()) {
            BufferedImage old = ImageIO.read(atlasFile);
            atlasW = Math.max(atlasW, old.getWidth());
            atlasH = Math.max(atlasH, old.getHeight());
        }

        // regenerate _low/_lowest from their full-size counterpart
        for (String[] e : entries) {
            String name = e[0];
            String base = null;
            if (name.endsWith("_low.png")) base = name.replace("_low.png", ".png");
            else if (name.endsWith("_lowest.png")) base = name.replace("_lowest.png", ".png");
            if (base == null) continue;

            File baseFile = new File(dir, base);
            if (!baseFile.exists()) { System.err.println("MISSING base " + base); continue; }
            int w = Integer.parseInt(e[3]), h = Integer.parseInt(e[4]);
            BufferedImage scaled = scale(ImageIO.read(baseFile), w, h);
            ImageIO.write(scaled, "png", new File(dir, name));
            System.out.println("regenerated " + name + " (" + w + "x" + h + ")");
        }

        // repaint atlas
        BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setComposite(AlphaComposite.Src);
        for (String[] e : entries) {
            File f = new File(dir, e[0]);
            if (!f.exists()) { System.err.println("MISSING " + e[0] + " — left blank in atlas"); continue; }
            BufferedImage img = ImageIO.read(f);
            int x = Integer.parseInt(e[1]), y = Integer.parseInt(e[2]);
            int w = Integer.parseInt(e[3]), h = Integer.parseInt(e[4]);
            if (img.getWidth() != w || img.getHeight() != h) {
                System.err.println("WARNING: " + e[0] + " is " + img.getWidth() + "x" + img.getHeight()
                        + " but structure says " + w + "x" + h + " — scaling to fit");
                img = scale(img, w, h);
            }
            g.drawImage(img, x, y, null);
        }
        g.dispose();
        ImageIO.write(atlas, "png", atlasFile);
        System.out.println("atlas_texture.png rebuilt (" + atlasW + "x" + atlasH + ", " + entries.size() + " regions)");
    }

    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }
}
