package org.kittisopikul.ProfileKitt;

import java.io.File;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.io.Opener;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

/*
 * Test driver following imglib2 examples
 */

public class Main {
	// within this method we define <T> to be a NumericType (depends on the type of ImagePlus)
	// you might want to define it as RealType if you know it cannot be an ImageJ RGB Color image
	public < T extends NumericType< T > & NativeType< T > > Main(ImageJ ij)
	{
		// define the file to open
		File file = new File( "DrosophilaWing.tif" );

		// open a file with ImageJ
		final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );

		// display it via ImageJ
		imp.show();
		
		//Line l = new Line(212,122,328,122);
		Line l = new Line(212,122,328,328);
		imp.setRoi(l);
		
	}

	public static void main( String[] args )
	{
		// open an ImageJ window
		ImageJ ij = new ImageJ();

		// run the example
		new Main(ij);
		IJ2Plugin p = new IJ2Plugin();
		p.run();
	}
}
