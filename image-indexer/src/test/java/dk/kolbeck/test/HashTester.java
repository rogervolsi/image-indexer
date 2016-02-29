package dk.kolbeck.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import dk.kolbeck.image.hash.ImagePHash;
import dk.kolbeck.image.object.ImageHashedType;

public class HashTester {
	// phash for original image
	static String PHASH_ORIGIN = null;
	static String testCase = "test_headoverheels";
	static int[] minWidthList = { 256, 512, 1024, 2048 };
	private int minWidth = 0;

	public void setMinWidth(int m) {
		this.minWidth = m;
	}

	public int getMinWidth() {
		return this.minWidth;
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 1) {
			testCase = args[0];
		} else if (args.length == 2) {
			testCase = args[0];
			PHASH_ORIGIN = args[1];
		} else {
			System.out.println("Ouch! ImageHashTester <testcase> <orig_phash>");
			return;
		}

		HashTester main = new HashTester();
		for (int i = 0; i < minWidthList.length; i++) {
			System.out.println("Testing with minWidth: " + minWidthList[i]);
			System.out.println("=============================================");
			main.setMinWidth(minWidthList[i]);
			main.walkDir(new File(testCase + "_images"), 0);
		}

		// main.testBase64();
	}

	public void walkDir(File rootDir, int level) {
		String PHASH_COMPARE_WITH = null;
		String[] everythingInThisDir = rootDir.list();
		for (String name : everythingInThisDir) {
			String fileName = rootDir.getPath() + File.separator + name;
			File file = new File(fileName);
			if (file.isFile()) {
				try {
		    ImagePHash phash = new ImagePHash();
					ImageHashedType it = new ImageHashedType();
					String hashString = phash.getHashAsString(new FileInputStream(file), getMinWidth());
					
					if(PHASH_COMPARE_WITH == null) {
							if(PHASH_ORIGIN != null) {
								PHASH_COMPARE_WITH = PHASH_ORIGIN;
							} else {
								PHASH_COMPARE_WITH = hashString;
							}
					}
					
					long distance = it.distance(PHASH_COMPARE_WITH, hashString);
					float score = (float) (Long.SIZE - distance) / (float) Long.SIZE;

					System.out.println("score: " + score + ", distance to org: " + distance + ", filename=" + file.getName()); // " + " [" + hashString + "]:[" + PHASH_ORG + "]");
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (file.isDirectory()) {
				walkDir(file, level + 1);
			}
		}
	}
}
