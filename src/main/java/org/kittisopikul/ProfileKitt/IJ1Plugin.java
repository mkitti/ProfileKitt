package org.kittisopikul.ProfileKitt;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Plot;
import ij.gui.PlotMaker;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.plugin.PlugIn;

/***
 * 
 * @author Mark Kittisopikul
 * @see ij.plugin.Profiler
 * Based on ij.plugin.Profiler
 */
public class IJ1Plugin implements PlotMaker, PlugIn {
	ImagePlus imp;
	boolean firstTime = true;

	@Override
	public void run(String arg) {
//		if (arg.equals("set"))
//		{doOptions(); return;}
		imp = IJ.getImage();
		Plot plot = getPlot();
		firstTime = false;
		if (plot==null)
			return;
		plot.setPlotMaker(this);
		plot.show();
	}

	@Override
	public Plot getPlot() {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine()) {
			if (firstTime)
				IJ.error("ProfileKitt", "Line selection required");
			return null;
		}
		ProfilePlot pp = new ProfilePlot(imp, false);
		return pp.getPlot();
	}

	@Override
	public ImagePlus getSourceImage() {
		return imp;
	}

}
