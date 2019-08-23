package nanoj.liveSRRF.gui;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.plugin.HyperStackConverter;
import ij.plugin.PlugIn;
import nanoj.core.java.io.LoadNanoJTable;
import nanoj.liveSRRF.MFMCalibration;

import java.io.IOException;
import java.util.Map;

import static nanoj.core.java.imagej.ResultsTableTools.dataMapToResultsTable;
import static nanoj.liveSRRF.gui.GetSpatialCalibrationMFMdata_.getSortedIndices;

public class ApplyCalibrationMFMdata_ implements PlugIn {

    private double[] shiftX, shiftY, theta, chosenROIsLocations, axialPositions, intCoeffs;
    private final String MFMApplyCalibVersion = "v0.5";

    @Override
    public void run(String s) {

        // ---- Getting input data ----
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) imp = IJ.openImage();
        if (imp == null) return;
        imp.show();

        IJ.log("\\Clear");  // Clear the log window
        IJ.log("-------------------------------------------------------------------------------------");
        IJ.log("MFM Correction ("+MFMApplyCalibVersion+")");

        ImageStack ims = imp.getImageStack().convertToFloat();
        int width = imp.getWidth();
        int height = imp.getHeight();
        int nFrames = imp.getStackSize();

        // ---- Getting calibration data from the NanoJ table ----
        IJ.log("Getting calibration file...");
        String calibTablePath = IJ.getFilePath("Choose Drift-Table to load...");
        if (calibTablePath == null) return;

        Map<String, double[]> calibTable;
        try {
            calibTable = new LoadNanoJTable(calibTablePath).getData();
            shiftX = calibTable.get("X-shift (pixels)");
            shiftY = calibTable.get("Y-shift (pixels)");
            theta = calibTable.get("Theta (degrees)");
            chosenROIsLocations = calibTable.get("ROI #");
            axialPositions = calibTable.get("Axial positions");
            intCoeffs = calibTable.get("Intensity scaling");
            ResultsTable rt = dataMapToResultsTable(calibTable);
            rt.show("Calibration-Table");
        } catch (IOException e) {
            IJ.log("Catching exception...");
            e.printStackTrace();
        }

        int nROI = shiftX.length;
        int nImageSplits = 3; // TODO: this is hardcoded for the moment
        int cropSizeX = Math.round(width/nImageSplits); // TODO: assume that size in X and Y are the same?
        int cropSizeY = Math.round(height/nImageSplits); // TODO: assume that size in X and Y are the same?

        ImageStack[] imsCorrectedArray = new ImageStack[nROI];
        ImageStack imsCorrectedUberStack = new ImageStack();

        double[] shiftXslice = new double[nFrames];
        double[] shiftYslice = new double[nFrames];
        double[] thetaSlice = new double[nFrames];
        double[] coeffSlice = new double[nFrames];
        int[] sortedIndicesROI = getSortedIndices(axialPositions);
        MFMCalibration RCCMcalculator = new MFMCalibration();

        int i,j,x,y;
        for (int id=0; id<nROI; id++){
            i = (int) chosenROIsLocations[id]%3;
            j = (int) (chosenROIsLocations[id] - i)/3;
//            IJ.log("i="+i);
//            IJ.log("j="+j);
            for (int k = 0; k < nFrames; k++){
                shiftXslice[k] = shiftX[id];
                shiftYslice[k] = shiftY[id];
                thetaSlice[k] = theta[id];
                coeffSlice[k] = intCoeffs[id];
            }
//                IJ.log("X-shift: "+shiftXslice[0]);

            x = Math.round((width/nImageSplits)*i);
            y = Math.round((height/nImageSplits)*j);
//            IJ.log("x="+x);
//            IJ.log("y="+y);
            ImageStack imsTemp = ims.crop(x, y, 0, cropSizeX,cropSizeY, nFrames);
            imsCorrectedArray[sortedIndicesROI[id]] = RCCMcalculator.applyMFMCorrection(imsTemp, shiftXslice, shiftYslice, thetaSlice, coeffSlice)[0];
        }

        IJ.log("Reshaping data...");
        for (int r = 0; r < nROI; r++) {
            for (int k = 0; k < nFrames; k++) {
                imsCorrectedUberStack.addSlice(imsCorrectedArray[r].getProcessor(k+1));
            }
        }

        ImagePlus impStack = new ImagePlus(imp.getShortTitle()+" - Calibrated", imsCorrectedUberStack);
        impStack.setTitle(imp.getShortTitle()+" - Calibrated stack");
        HyperStackConverter hsC = new HyperStackConverter();
        impStack = hsC.toHyperStack(impStack, 1, nROI, nFrames, "xyctz", "Color");
        impStack.show();

        IJ.log("------------");
        IJ.log("All done.");
    }
}
