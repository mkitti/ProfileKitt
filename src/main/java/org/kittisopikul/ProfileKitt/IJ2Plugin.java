package org.kittisopikul.ProfileKitt;

import java.util.Arrays;

import org.scijava.ItemIO;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultDoubleTable;
import org.scijava.table.DoubleTable;
import org.scijava.ui.UIService;
import org.scijava.util.TreeNode;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.RoiListener;
import net.imagej.Dataset;
import net.imagej.legacy.convert.roi.line.IJLineToLineConverter;
import net.imagej.legacy.convert.roi.line.IJLineWrapper;
import net.imagej.roi.ROIService;
import net.imagej.roi.ROITree;
import net.imglib2.RealPositionable;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.LanczosInterpolator;
import net.imglib2.interpolation.randomaccess.LanczosInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolator;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolator;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.roi.geom.real.Line;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Test interpolation methods along line profiles defined by a ROI
 * 
 * @see ij.plugin.Profiler
 * @author Mark Kittisopikul
 *
 */

@Plugin(name="ProfileKitt", type = IJ2Plugin.class, menuPath = "Analyze > ProfileKitt")
public class IJ2Plugin extends InteractiveCommand implements RoiListener {
	
	private Img<?> img;
		
	//Includes image and ROI data
	@Parameter
	private Dataset dataset;
	
	@Parameter
	private ROIService roiService;
	
	//Taking a ROI as an input parameter does not work
	//@Parameter(label="line_roi")
	private Line line;
	
	@Parameter
	private UIService uiService;
	
	@Parameter(type=ItemIO.OUTPUT)
	DoubleTable table;

	//In case we need to manually convert a ImageJ1 ROI to a imglib2 ROI
	final IJLineToLineConverter converter = new IJLineToLineConverter();

	@Override
	public void run() {
		System.out.println("Img: " + img);
		if(dataset == null) {
			//Services failed, use ImageJ1 API
			ImagePlus imp = IJ.getImage();
			Roi roi = imp.getRoi();
			Roi.addRoiListener(this);
			if(roi.isLine())
				plotProfile(imp,converter.convert((ij.gui.Line)imp.getRoi()));
			else
				IJ.error("ProfileKitt", "Line selection required");
			
		} else {
			//Use ImageJ2 service API
			ROITree rois = roiService.getROIs(dataset);
			if(rois == null)
				return;
			img = dataset;
			for(TreeNode<?> node: rois.children()) {
				Object data = node.data();
				System.out.println(data);
				if(data instanceof Line) {
					System.out.println(img.firstElement().getClass());
					//TODO: Verify type of image.
					plotProfile((Img<RealType>) img,(Line)data);
					if(data instanceof IJLineWrapper) {
						//Add ImageJ1 ROI listener
						((IJLineWrapper) data).getRoi().addRoiListener(this);
					}
					break;
				}
			}
		}	
	}
	public < T extends RealType< T > & NativeType< T > > void plotProfile(ImagePlus imp2, ij.gui.Line line) {
		plotProfile(imp2,converter.convert(line));
	}
	public < T extends RealType< T > & NativeType< T > > void plotProfile(ImagePlus imp2, Line line) {
		final Img<T> image = ImagePlusAdapter.wrap( imp2 );
		//TODO: See if frame and z coordinate are needed for ndims > 2
		int frame, z;
		frame = imp2.getFrame();
		z = imp2.getZ();
		plotProfile(image,line);
	}
	public < T extends RealType< T > > void plotProfile(Img< T > image,Line line) {
		final NearestNeighborInterpolatorFactory<T> nn_factory = new NearestNeighborInterpolatorFactory<T>();
		final NLinearInterpolatorFactory<T> lin_factory = new NLinearInterpolatorFactory<T>();
		final LanczosInterpolatorFactory<T> lanczos_factory = new LanczosInterpolatorFactory<T>();
		//end points of the line
		double[] pt_1, pt_2;
		//difference vector between the points; delta_per_sample = delta / nSamples
		double[] delta, delta_per_sample;
		//arrays to collect the samples per the points
		double[] nn_values, lin_values, lanczos_values;
		//magnitude of delta
		double distance;
		//number of samples, dimensions
		int nSamples, ndims;
		
		
		// Save line in case we get a callback
		this.line = line;
		
		if(line == null)
			return;
		
		//setup nD vectors
		ndims = image.numDimensions();
		
		System.out.println("NDims: " + ndims);
		
		pt_1 = new double[ndims];
		pt_2 = new double[ndims];
		delta = new double[ndims];
		delta_per_sample = new double[ndims];

		// calculate the distance between the points
		line.endpointOne().localize(pt_1);
		line.endpointTwo().localize(pt_2);
		distance = 0;
		for(int d=0; d < ndims; d++) {
			delta[d] = pt_2[d]-pt_1[d];
			distance += delta[d] * delta[d];
		}
		distance = java.lang.Math.sqrt(distance);
		nSamples = (int)java.lang.Math.ceil(distance)+1;
		nSamples = Math.max(nSamples, 2);
		
		for(int d=0; d < ndims; d++) {
			delta_per_sample[d] = delta[d]/(nSamples-1);
		}
			
		System.out.println("Distance: " + distance);
		System.out.println("Pt 1 X: " + pt_1[0]);
		
		//Round
		NearestNeighborInterpolator<T> nn = nn_factory.create(image);
		//Floor
		NLinearInterpolator<T> lin = lin_factory.create(image);
		//FloorOffset
		LanczosInterpolator<T> lanczos = lanczos_factory.create(image);
		RealPositionable[] interpolators = {nn, lin, lanczos};
		
		nn_values = new double[nSamples];
		lin_values = new double[nSamples];
		lanczos_values = new double[nSamples];
		
		System.out.println(Arrays.toString(delta_per_sample));
		
		//Set the interpolators to the first point of the ROI
		//TODO: Offset based on interpolator type(Round or Floor)
		for(RealPositionable a: interpolators) {
			a.setPosition(pt_1);
		}
		for(int s=0; s < nSamples; s++) {
			for(RealPositionable a: interpolators) {
				a.move(delta_per_sample);
			}
			//System.out.println("s:" + s + " NN: " + nn.get() + " Lanczos: " + lanczos.get());
			nn_values[s] = nn.get().getRealDouble();
			lin_values[s] = lin.get().getRealDouble();
			lanczos_values[s] = lanczos.get().getRealDouble();
		}
		
		//Create table for output
		table = new DefaultDoubleTable(3,nSamples);
		table.setColumnHeader(0, "Nearest Neighbor");
		table.get(0).fill(nn_values);
		table.setColumnHeader(1, "NLinear");
		table.get(1).fill(lin_values);
		table.setColumnHeader(2, "Lanczos");
		table.get(2).fill(lanczos_values);
		
		System.out.print(table);
		
		//TODO: Consider actual graphical output like scijava-plot
	}
	@Override
	public void roiModified(ImagePlus imp, int id) {
		//Callback from ImageJ1 ROI
		System.out.println("ROI Modified");
		System.out.println(imp);
		System.out.println(id);
		switch(id) {
		case RoiListener.DELETED:
			System.out.print("Deleted?: " + line);
			line = null;
			break;
		case RoiListener.MODIFIED:
		case RoiListener.MOVED:
			plotProfile(imp,line);
			break;
		}
	}


}
