import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pixel-art generator for the Next Step icons. Drawn by hand at 16x16 rather
 * than rendered with Graphics2D, because antialiasing at this size turns
 * everything to mush on RuneLite's dark sidebar.
 * <p>
 * Usage: {@code java tools/MakeIcon.java <out.png> [scale]}
 * <ul>
 * <li>scale 1 (the default) is the sidebar nav button,
 *     {@code src/main/resources/com/nextstep/icon.png}.
 * <li>scale 3 is the plugin hub's listing icon, {@code icon.png} in the
 *     repository root, which the hub allows up to 48x72.
 * </ul>
 * The larger one is the same art scaled by whole pixels, not a redraw: the hub
 * listing and the sidebar button should be recognisably one icon, and
 * nearest-neighbour keeps the pixel edges hard instead of smearing them.
 */
public class MakeIcon
{
	// Ascending staircase = progression, "the next step up".
	static final int STEP = 0xFFE4E4E4;   // near-white, reads on dark gray
	static final int ARROW = 0xFFFF981F;  // RuneLite brand orange

	public static void main(String[] a) throws Exception
	{
		int scale = a.length > 1 ? Integer.parseInt(a[1]) : 1;
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		// Three solid treads, each 4 wide, rising 3px per step, sharing a baseline.
		fill(img, 1, 4, 12, 14, STEP);
		fill(img, 5, 8, 9, 14, STEP);
		fill(img, 9, 12, 6, 14, STEP);

		// Up-and-right arrow above the top tread.
		int[][] shaft = {{9,5},{10,4},{11,3},{12,2},{13,1},{10,5},{11,4},{12,3},{13,2}};
		for (int[] p : shaft)
		{
			img.setRGB(p[0], p[1], ARROW);
		}
		// Arrowhead: corner bracket at the top right.
		for (int x = 11; x <= 14; x++)
		{
			img.setRGB(x, 0, ARROW);
		}
		for (int y = 0; y <= 3; y++)
		{
			img.setRGB(14, y, ARROW);
		}

		if (scale > 1)
		{
			img = upscale(img, scale);
		}

		ImageIO.write(img, "png", new File(a[0]));
		System.out.println("wrote " + a[0] + " at " + img.getWidth() + "x" + img.getHeight());
	}

	/** Nearest-neighbour, so one source pixel becomes a hard scale x scale block. */
	static BufferedImage upscale(BufferedImage src, int scale)
	{
		BufferedImage out = new BufferedImage(
			src.getWidth() * scale, src.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < out.getWidth(); x++)
		{
			for (int y = 0; y < out.getHeight(); y++)
			{
				out.setRGB(x, y, src.getRGB(x / scale, y / scale));
			}
		}
		return out;
	}

	static void fill(BufferedImage img, int x0, int x1, int y0, int y1, int rgb)
	{
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				img.setRGB(x, y, rgb);
			}
		}
	}
}
