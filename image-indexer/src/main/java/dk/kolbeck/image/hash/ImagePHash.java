package dk.kolbeck.image.hash;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import dk.kolbeck.image.object.ImageHashedType;

/*
 * pHash-like image hash. 
 * Based On: <br/> 
 * http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html <br/>
 * https://www.memonic.com/user/aengus/folder/coding/id/1qVeq <br/>
 * http://phash.org/ <br/>
 */
public class ImagePHash {

	private int size = 32;
	private int smallerSize = 8;

	public String get_IMAGE_ORG() {
		return _IMAGE_ORG;
	}

	public String get_IMAGE_RESIZE() {
		return _IMAGE_RESIZE;
	}

	public String get_IMAGE_GREYSCALE() {
		return _IMAGE_GREYSCALE;
	}

	private String _IMAGE_ORG;
	private String _IMAGE_RESIZE;
	private String _IMAGE_GREYSCALE;

	public ImagePHash() {
		initCoefficients();
	}

	public ImagePHash(int size, int smallerSize) {
		super();
		this.size = size;
		this.smallerSize = smallerSize;
	}

	/**
	 * Calculate phash as 64-bit long
	 * 
	 * @param is
	 *            - InputStream to image file
	 * @param minWidth
	 *            - If width is less than minWidth, the image will be scaled and
	 *            blurred before processing
	 * @return phash
	 * @throws IOException
	 */
	public long getHash(InputStream is, int minWidth) throws IOException {
		BufferedImage img = ImageIO.read(is);

		// optimizing technic for small images
		if (img.getWidth() < minWidth) {
			float factor = 1 + (float) minWidth / (float) img.getWidth();

			img = resize(img, (int) (img.getWidth() * factor), (int) (img.getHeight() * factor));

			img = blur(img);
			// img = gaussian(img);
		}

		this._IMAGE_ORG = img2PNGBase64(img);

		/*
		 * 1. Reduce size. Like Average Hash, pHash starts with a small image.
		 * However, the image is larger than 8x8; 32x32 is a good size. This is
		 * really done to simplify the DCT computation and not because it is
		 * needed to reduce the high frequencies.
		 */
		img = resize(img, size, size);
		this._IMAGE_RESIZE = img2PNGBase64(img);

		/*
		 * 2. Reduce color. The image is reduced to a grayscale just to further
		 * simplify the number of computations.
		 */
		img = grayscale(img);
		this._IMAGE_GREYSCALE = img2PNGBase64(img);

		double[][] vals = new double[size][size];

		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				vals[x][y] = getBlue(img, x, y);
			}
		}

		/*
		 * 3. Compute the DCT. The DCT separates the image into a collection of
		 * frequencies and scalars. While JPEG uses an 8x8 DCT, this algorithm
		 * uses a 32x32 DCT.
		 */
		double[][] dctVals = applyDCT(vals);

		/*
		 * 4. Reduce the DCT. This is the magic step. While the DCT is 32x32,
		 * just keep the top-left 8x8. Those represent the lowest frequencies in
		 * the picture.
		 */
		/*
		 * 5. Compute the average value. Like the Average Hash, compute the mean
		 * DCT value (using only the 8x8 DCT low-frequency values and excluding
		 * the first term since the DC coefficient can be significantly
		 * different from the other values and will throw off the average).
		 */
		double total = 0;

		for (int x = 0; x < smallerSize; x++) {
			for (int y = 0; y < smallerSize; y++) {
				total += dctVals[x][y];
			}
		}
		total -= dctVals[0][0];

		double avg = total / (double) ((smallerSize * smallerSize) - 1);

		/*
		 * 6. Further reduce the DCT. This is the magic step. Set the 64 hash
		 * bits to 0 or 1 depending on whether each of the 64 DCT values is
		 * above or below the average value. The result doesn't tell us the
		 * actual low frequencies; it just tells us the very-rough relative
		 * scale of the frequencies to the mean. The result will not vary as
		 * long as the overall structure of the image remains the same; this can
		 * survive gamma and color histogram adjustments without a problem.
		 */
		long hashBits = 0;

		for (int x = 0; x < smallerSize; x++) {
			for (int y = 0; y < smallerSize; y++) {
				hashBits = (dctVals[x][y] > avg ? (hashBits << 1) | 0x01 : (hashBits << 1) & 0xFFFFFFFFFFFFFFFEl);
			}
		}

		hash = Long.toBinaryString(hashBits);
		return hashBits;
	}

	/**
	 * Calculate phash as String 64 chars wide
	 * 
	 * @param is
	 *            - InputStream to image file
	 * @param minWidth
	 *            - If width is less than minWidth, the image will be scaled and
	 *            blurred before processing
	 * @return phash
	 * @throws IOException
	 */
	public String getHashAsString(InputStream is, int minWidth) throws IOException {
		if (hash == null)
			hash = Long.toBinaryString(getHash(is, minWidth));
		return hash;
	}

	/**
	 * Creates an ImageHashedYType object with image and phash information
	 * 
	 * @param filename
	 *            - name of image file
	 * @param minWidth
	 *            - If width is less than minWidth, the image will be scaled and
	 *            blurred before processing
	 * @return ImageHashedType
	 * @throws IOException
	 */
	public ImageHashedType getImageTypeWithHash(String filename, int minWidth) throws IOException {
		ImageHashedType result = new ImageHashedType();
		result.setFilename(((File) new File(filename)).getName());

		FileInputStream is = new FileInputStream(filename);
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
		if (!iter.hasNext()) {
			throw new RuntimeException("No readers found!");
		}
		ImageReader reader = iter.next();
		reader.setInput(iis);
		result.setFormat(reader.getFormatName());
		result.setWidth(reader.getWidth(0));
		result.setHeight(reader.getHeight(0));
		result.setAspect(reader.getAspectRatio(0));
		is.close();
		iis.close();

		is = new FileInputStream(filename);
		result.setPhash(getHash(is, minWidth));
		is.close();
		return result;
	}

	/**
	 * Creates an ImageHashedYType object with image and phash information
	 * 
	 * @param filename
	 *            - name of image file
	 * @param base64data
	 *            - base64 data containing image
	 * @param minWidth
	 *            - If width is less than minWidth, the image will be scaled and
	 *            blurred before processing
	 * @return ImageHashedType
	 * @throws IOException
	 */
	public ImageHashedType getImageTypeWithHash(String filename, String base64data, int minWidth) throws IOException {
		ImageHashedType result = new ImageHashedType();
		result.setFilename(filename);

		InputStream is = Base64.getDecoder().wrap(new ByteArrayInputStream(base64data.getBytes()));
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
		if (!iter.hasNext()) {
			throw new RuntimeException("No readers found!");
		}
		ImageReader reader = iter.next();
		reader.setInput(iis);
		result.setFormat(reader.getFormatName());
		result.setWidth(reader.getWidth(0));
		result.setHeight(reader.getHeight(0));
		result.setAspect(reader.getAspectRatio(0));
		is.close();
		iis.close();

		is = Base64.getDecoder().wrap(new ByteArrayInputStream(base64data.getBytes()));
		result.setPhash(getHash(is, minWidth));
		is.close();

		return result;
	}

	private String hash = null;

	private BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = resizedImage.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return resizedImage;
	}

	private ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

	private BufferedImage grayscale(BufferedImage img) {
		colorConvert.filter(img, img);
		return img;
	}

	private BufferedImage gaussian(BufferedImage image) {
		image = getGaussianBlurFilter(20, true).filter(image, null);
		image = getGaussianBlurFilter(20, false).filter(image, null);
		return image;
	}

	private ConvolveOp getGaussianBlurFilter(int radius, boolean horizontal) {
		if (radius < 1) {
			throw new IllegalArgumentException("Radius must be >= 1");
		}

		int size = radius * 2 + 1;
		float[] data = new float[size];

		float sigma = radius / 3.0f;
		float twoSigmaSquare = 2.0f * sigma * sigma;
		float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
		float total = 0.0f;

		for (int i = -radius; i <= radius; i++) {
			float distance = i * i;
			int index = i + radius;
			data[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
			total += data[index];
		}

		for (int i = 0; i < data.length; i++) {
			data[i] /= total;
		}

		Kernel kernel = null;
		if (horizontal) {
			kernel = new Kernel(size, 1, data);
		} else {
			kernel = new Kernel(1, size, data);
		}
		return new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
	}

	private BufferedImage blur(BufferedImage img) {
		BufferedImage biDest = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

		float data[] = { 0.0625f, 0.125f, 0.0625f, 0.125f, 0.25f, 0.125f, 0.0625f, 0.125f, 0.0625f };
		Kernel kernel = new Kernel(3, 3, data);
		ConvolveOp convolve = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
		convolve.filter(img, biDest);
		return biDest;
	}

	private static int getBlue(BufferedImage img, int x, int y) {
		return (img.getRGB(x, y)) & 0xff;
	}

	// DCT function stolen from
	// http://stackoverflow.com/questions/4240490/problems-with-dct-and-idct-algorithm-in-java

	private double[] c;

	private void initCoefficients() {
		c = new double[size];

		for (int i = 1; i < size; i++) {
			c[i] = 1;
		}
		c[0] = 1 / Math.sqrt(2.0);
	}

	private double[][] applyDCT(double[][] f) {
		int N = size;

		double[][] F = new double[N][N];
		for (int u = 0; u < N; u++) {
			for (int v = 0; v < N; v++) {
				double sum = 0.0;
				for (int i = 0; i < N; i++) {
					for (int j = 0; j < N; j++) {
						sum += Math.cos(((2 * i + 1) / (2.0 * N)) * u * Math.PI) * Math.cos(((2 * j + 1) / (2.0 * N)) * v * Math.PI) * (f[i][j]);
					}
				}
				sum *= ((c[u] * c[v]) / 4.0);
				F[u][v] = sum;
			}
		}
		return F;
	}

	private String img2PNGBase64(BufferedImage img) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "PNG", out);
		byte[] bytes = out.toByteArray();

		String base64bytes = Base64.getEncoder().encodeToString(bytes);
		String src = "data:image/png;base64," + base64bytes;
		return src;
	}
}