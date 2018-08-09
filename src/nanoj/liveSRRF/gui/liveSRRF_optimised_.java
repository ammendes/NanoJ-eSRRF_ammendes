package nanoj.liveSRRF.gui;

import com.jogamp.opencl.CLDevice;
import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.PointRoi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.io.zip.SaveFileInZip;
import nanoj.core.java.io.zip.virtualStacks.FullFramesVirtualStack;
import nanoj.core2.NanoJPrefs;
import nanoj.core2.NanoJProfiler;
import nanoj.core2.NanoJUsageTracker;
import org.python.modules.math;
import nanoj.liveSRRF.liveSRRF_CL;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static java.lang.Math.min;
import static nanoj.core2.NanoJCrossCorrelation.calculateCrossCorrelationMap;

public class liveSRRF_optimised_ implements PlugIn {

    // Basic formats
    private int magnification,
            nFrameForSRRF,
            sensitivity,
            frameGap,
            nFrameOnGPU,
            nSRRFframe,
            nSlices,
            width,
            height,
            blockSize,
            nGPUloadPerSRRFframe;

    private float fwhm, maxMemoryGPU;

    private boolean correctVibration,
            calculateAVG,
            calculateSTD,
            getInterpolatedImage,
            writeToDisk,
            previousWriteToDisk = false;

    private final int radiusCCM = 5;
    private final String LiveSRRFVersion = "v1.0";
    private String pathToDisk = "",
            fileName,
            chosenDeviceName;

    private float[] shiftX, shiftY;

    // Image formats
    private ImagePlus imp;
    private ImagePlus impCCM = null;

    private ImagePlus impSRRFavg,
            impSRRFstd,
            impRawInterpolated;


    // Advanced formats
    private NanoJPrefs prefs = new NanoJPrefs(this.getClass().getName());
    private NanoJProfiler prof = new NanoJProfiler();
    private liveSRRF_CL liveSRRF;
    private SaveFileInZip saveFileInZip;
    CLDevice chosenDevice = null;

    // Tracker
    private String user = "FijiUpdater";
    private String version = "20180809-" + user;
    private NanoJUsageTracker tracker = new NanoJUsageTracker("NanoJ-LiveSRRF", version, "UA-61590656-4");


    @Override
    public void run(String arg) {

        tracker.logUsage("LiveSRRF_");

        // Get raw data
        imp = WindowManager.getCurrentImage();
        if (imp == null) imp = IJ.openImage();
        if (imp == null) return;
        imp.show();

        nSlices = imp.getImageStack().getSize();
        width = imp.getImageStack().getWidth();
        height = imp.getImageStack().getHeight();

        IJ.log("\\Clear");  // Clear the log window
        IJ.log("-------------------------------------");
        IJ.log("-------------------------------------");
        IJ.log("liveSRRF " + LiveSRRFVersion);
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        IJ.log(now.format(formatter));
//        liveSRRF = new liveSRRF_CL();
////        CLDevice[] allDevices = liveSRRF.checkDevices();
////
////        // Initilizaing string for device choice
////        String[] deviceNames = new String[allDevices.length + 1];
////        deviceNames[0] = "Default device";
////
////        for (int i = 1; i <= allDevices.length; i++) {
////            deviceNames[i] = allDevices[i - 1].getName();
////        }

//        // Build GUI
//        Font headerFont = new Font("Arial", Font.BOLD, 16);
//        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("liveSRRF " + LiveSRRFVersion);
//        gd.addMessage("-=-= SRRF parameters =-=-\n", headerFont);
//        gd.addNumericField("Magnification (default: 5)", prefs.get("magnification", 5), 0);
//        gd.addNumericField("Radius (pixels, default: 2)", prefs.get("fwhm", 2), 2);
//        gd.addNumericField("Sensitivity (default: 1)", prefs.get("sensitivity", 1), 0);
//        gd.addNumericField("# frames for SRRF (0 = auto)", prefs.get("nFrameForSRRF", 0), 0);
//        gd.addCheckbox("Correct vibration", prefs.get("correctVibration", false));
//
//        gd.addMessage("-=-= Reconstructions =-=-\n", headerFont);
//        gd.addCheckbox("AVG reconstruction (default: on)", prefs.get("calculateAVG", true));
//        gd.addCheckbox("STD reconstruction (default: off)", prefs.get("calculateSTD", false));
//        gd.addCheckbox("Wide-field interpolation (default: off)", prefs.get("getInterpolatedImage", false));
//
//        gd.addMessage("-=-= Advanced settings =-=-\n", headerFont);
//        gd.addCheckbox("Show advanced settings", prefs.get("showAdvSettings", false));
//
//        gd.addMessage("-=-= Rolling analysis =-=-\n", headerFont);
//        gd.addNumericField("# frame gap between SR frame (0 = auto)", prefs.get("frameGap", 0), 0);
//        gd.addMessage("Warning: Rolling analysis may lead to long computation times.");
//
//        gd.addMessage("-=-= GPU/CPU processing =-=-\n", headerFont);
//        gd.addChoice("Processing device", deviceNames, prefs.get("chosenDeviceName", "Default device"));
//        gd.addNumericField("Maximum amount of memory on device (MB, default: 1000)", prefs.get("maxMemoryGPU", 500), 2);
//        gd.addMessage("Giving liveSRRF access to a lot of memory speeds up the reconstruction\n" +
//                "but may slow down the graphics card for your Minecraft game that you have \n" +
//                "running in parallel.");
//
//        gd.addNumericField("Analysis block size (default: 20000)", prefs.get("blockSize", 20000), 0);
//        gd.addMessage("A large analysis block size will speed up the analysis but will use\n" +
//                "more resources and may slow down your computer.");
//
//        gd.addMessage("-=-= Write to disk =-=-\n", headerFont);
//        gd.addCheckbox("Directly write to disk (default: off)", false);
//        gd.addMessage("Writing directly to disk will slow down the reconstruction but \n" +
//                "will allow for long time courses to be reconstructed without\n" +
//                "exceeding RAM capacity.");
//
//        MyDialogListenerMainGUI dl = new MyDialogListenerMainGUI(); // this serves to estimate a few indicators such as RAM usage
//        gd.addDialogListener(dl);
//        gd.showDialog();
//
//        // If the GUI was cancelled
//        if (gd.wasCanceled()) {
//            liveSRRF.release();
//            return;
//        }

        mainGUI();

//        // Get chosen device
//        CLDevice chosenDevice = null;
//        for (CLDevice allDevice : allDevices) {
//            if (chosenDeviceName.equals(allDevice.getName())) chosenDevice = allDevice;
//        }

        // Save last user entries
        savePreferences();

        IJ.log("-------------------------------------");
        IJ.log("Parameters:");
        IJ.log("Magnification: " + magnification);
        IJ.log("FWHM: " + fwhm + " pixels");
        IJ.log("Sensitivity: " + sensitivity);
        IJ.log("# frames for SRRF: " + nFrameForSRRF);
        IJ.log("# frames gap: " + frameGap);
        IJ.log("# frames on GPU: " + nFrameOnGPU);
        IJ.log("Estimated GPU memory usage: " + Math.round(predictMemoryUsed(nFrameOnGPU)[0]) + " MB");
        IJ.log("Estimated RAM usage: " + Math.round(predictMemoryUsed(nFrameOnGPU)[1]) + " MB");
        IJ.log("Running on: " + chosenDeviceName);
        IJ.log("# reconstructed frames: " + nSRRFframe);
        IJ.log("# GPU load / SRRF frame: " + nGPUloadPerSRRFframe);

        // Initialize ZipSaver in case we're writing to disk
        if (writeToDisk) {
            fileName = pathToDisk + imp.getTitle() + " - liveSRRF.zip";
            IJ.log("Write to disk enabled");
            IJ.log(fileName);

            try {
                saveFileInZip = new SaveFileInZip(fileName, false);
            } catch (IOException e) {
                IJ.error("Whoops, it seems that " + fileName + " doesn't exist. At least there's ice-cream...");
                e.printStackTrace();
            }
        }

        // Initialize variables
        int indexStartSRRFframe;
        int nFrameToLoad;
        liveSRRF.initialise(width, height, magnification, fwhm, sensitivity, nFrameOnGPU, nFrameForSRRF, blockSize, chosenDevice);

        shiftX = new float[nFrameForSRRF];
        shiftY = new float[nFrameForSRRF];

        ImageStack imsBuffer;

        ImageStack imsRawData;
        ImageStack imsSRRFavg = new ImageStack(width * magnification, height * magnification);
        ImageStack imsSRRFstd = new ImageStack(width * magnification, height * magnification);
        ImageStack imsRawInterpolated = new ImageStack(width * magnification, height * magnification);

        ImagePlus impTemp = new ImagePlus();
        impTemp.copyScale(imp); // make sure we copy the pixel sizes correctly across
        Calibration cal = impTemp.getCalibration();
        cal.pixelWidth /= magnification;
        cal.pixelHeight /= magnification;
        cal.setUnit(imp.getCalibration().getUnit());

        boolean userPressedEscape;

        // Start looping trough SRRF frames --------------------------------------------
        for (int r = 1; r <= nSRRFframe; r++) {
            liveSRRF.resetFramePosition();

            IJ.log("--------");
            IJ.log("SRRF frame: " + r + "/" + nSRRFframe);
            indexStartSRRFframe = (r - 1) * frameGap + 1;
            IJ.log("Stack index start: " + indexStartSRRFframe);
            if (correctVibration) calculateShiftArray(indexStartSRRFframe);
            liveSRRF.loadShiftXYGPUbuffer(shiftX, shiftY);

            IJ.showProgress(r - 1, nSRRFframe);

            for (int l = 0; l < nGPUloadPerSRRFframe; l++) {

                IJ.showStatus("Estimated time remaining: ");


//                IJ.log("----------------------------");
                nFrameToLoad = min(nFrameOnGPU, nFrameForSRRF - nFrameOnGPU * l);
//                IJ.log("Number of frames to load: " + nFrameToLoad);
//                IJ.log("Index start: " + (indexStartSRRFframe + nFrameOnGPU * l));

                imsRawData = new ImageStack(width, height);
                for (int f = 0; f < nFrameToLoad; f++) {
                    imp.setSlice(indexStartSRRFframe + nFrameOnGPU * l + f);
                    imsRawData.addSlice(imp.getProcessor());
                }

                userPressedEscape = liveSRRF.calculateSRRF(imsRawData);

                // Check if user is cancelling calculation
                if (userPressedEscape) {
                    liveSRRF.release();
                    IJ.log("-------------------------------------");
                    IJ.log("Reconstruction aborted by user.");
                    return;
                }

            }

            imsBuffer = liveSRRF.readSRRFbuffer();

            if (writeToDisk) {
                try {
                    if (calculateAVG) {
                        impTemp = new ImagePlus("\"liveSRRF (AVG) - frame=" + r + ".tif", imsBuffer.getProcessor(1));
                        impTemp.setCalibration(cal);
                        saveFileInZip.addTiffImage("liveSRRF (AVG) - frame=" + r, impTemp);
                    }
                    if (calculateSTD) {
                        impTemp = new ImagePlus("\"liveSRRF (STD) - frame=" + r + ".tif", imsBuffer.getProcessor(2));
                        impTemp.setCalibration(cal);
                        saveFileInZip.addTiffImage("liveSRRF (STD) - frame=" + r, impTemp);
                    }
                    if (getInterpolatedImage) {
                        impTemp = new ImagePlus("\"liveSRRF (INT) - frame=" + r + ".tif", imsBuffer.getProcessor(3));
                        impTemp.setCalibration(cal);
                        saveFileInZip.addTiffImage("liveSRRF (INT) - frame=" + r, impTemp);
                    }
                } catch (IOException e) {
                    IJ.error("Whoops, it seems that there was a problem with saving to disk... (insert sad face here).");
                    e.printStackTrace();
                }


            } else {
                if (calculateAVG) imsSRRFavg.addSlice(imsBuffer.getProcessor(1));
                if (calculateSTD) imsSRRFstd.addSlice(imsBuffer.getProcessor(2));
                if (getInterpolatedImage) imsRawInterpolated.addSlice(imsBuffer.getProcessor(3));
            }
            IJ.log("RAM used: " + IJ.freeMemory());
        }
        // End looping trough SRRF frames --------------------------------------------


        // Release the GPU
        liveSRRF.release();

        IJ.log("-------------------------------------");
        // Close the ZipSaver & display stack as virtual stacks
        if (writeToDisk) {
            IJ.log("Results displayed as virtual stacks.");

            try {
                saveFileInZip.close();
            } catch (IOException e) {
                IJ.error("Error closing the ZipSaver !!!!!");
                e.printStackTrace();
            }

            try {
                if (calculateAVG) {
                    FullFramesVirtualStack vsimsSRRFavg = new FullFramesVirtualStack(fileName, true);
                    for (int f = vsimsSRRFavg.getSize(); f > 0; f--) {
                        if (!vsimsSRRFavg.getSliceLabel(f).contains("AVG")) vsimsSRRFavg.deleteSlice(f);
                    }

                    impSRRFavg = new ImagePlus(imp.getTitle() + " - liveSRRF (AVG)", vsimsSRRFavg);
                    impSRRFavg.setCalibration(cal);
                    IJ.run(impSRRFavg, "Enhance Contrast", "saturated=0.5");
                    impSRRFavg.show();
                }

                if (calculateSTD) {
                    FullFramesVirtualStack vsimsSRRFstd = new FullFramesVirtualStack(fileName, true);
                    for (int f = vsimsSRRFstd.getSize(); f > 0; f--) {
                        if (!vsimsSRRFstd.getSliceLabel(f).contains("STD")) vsimsSRRFstd.deleteSlice(f);
                    }

                    impSRRFstd = new ImagePlus(imp.getTitle() + " - liveSRRF (STD)", vsimsSRRFstd);
                    impSRRFstd.setCalibration(cal);
                    IJ.run(impSRRFstd, "Enhance Contrast", "saturated=0.5");
                    impSRRFstd.show();
                }

                if (getInterpolatedImage) {
                    FullFramesVirtualStack vsimsRawInterpolated = new FullFramesVirtualStack(fileName, true);
                    for (int f = vsimsRawInterpolated.getSize(); f > 0; f--) {
                        if (!vsimsRawInterpolated.getSliceLabel(f).contains("INT")) vsimsRawInterpolated.deleteSlice(f);
                    }

                    impRawInterpolated = new ImagePlus(imp.getTitle() + " - interpolated image", vsimsRawInterpolated);
                    impRawInterpolated.setCalibration(cal);
                    IJ.run(impRawInterpolated, "Enhance Contrast", "saturated=0.5");
                    impRawInterpolated.show();
                }

            } catch (IOException e) {
                IJ.error("Whoops, it seems that there was a problem with opening from disk... (insert sad face here).");
                e.printStackTrace();
            }


        } else {

            //Display results
            if (calculateAVG) {
                impSRRFavg = new ImagePlus(imp.getTitle() + " - liveSRRF (AVG)", imsSRRFavg);
                impSRRFavg.setCalibration(cal);
                IJ.run(impSRRFavg, "Enhance Contrast", "saturated=0.5");
                impSRRFavg.show();
            }

            if (calculateSTD) {
                impSRRFstd = new ImagePlus(imp.getTitle() + " - liveSRRF (STD)", imsSRRFstd);
                impSRRFstd.setCalibration(cal);
                IJ.run(impSRRFstd, "Enhance Contrast", "saturated=0.5");
                impSRRFstd.show();
            }

            if (getInterpolatedImage) {
                impRawInterpolated = new ImagePlus(imp.getTitle() + " - interpolated image", imsRawInterpolated);
                impRawInterpolated.setCalibration(cal);
                IJ.run(impRawInterpolated, "Enhance Contrast", "saturated=0.5");
                impRawInterpolated.show();
            }
        }


        // Bye-bye and report
        IJ.log("Memory usage: " + IJ.freeMemory());  // this also runs the garbage collector
        IJ.log("Thank you for your custom on this beautiful day !");
        now = LocalDateTime.now();
        IJ.log(now.format(formatter));
//        IJ.log("-------------------------------------");
//        IJ.log(prof.report());
    }


    //    -------------------------------------------------------------------------------------
    //    -------------------------- Here lies dragons and functions --------------------------
    //    -------------------------------------------------------------------------------------

    // --- Main GUI Dialog listener ---
    class MyDialogListenerMainGUI implements DialogListener {
        @Override
        public boolean dialogItemChanged(GenericDialog gd, AWTEvent awtEvent) {

            return grabSettingsMainGUI(gd);
        }
    }

    // --- Advanced GUI Dialog listener ---
    class MyDialogListenerAdvancedGUI implements DialogListener {
        @Override
        public boolean dialogItemChanged(GenericDialog gd, AWTEvent awtEvent) {

            return grabSettingsAdvancedGUI(gd);
        }
    }

    //    --- Grab settings from main GUI ---
    private boolean grabSettingsMainGUI(GenericDialog gd) {

        boolean goodToGo = false;

        magnification = (int) gd.getNextNumber();
        fwhm = (float) gd.getNextNumber();
        sensitivity = (int) gd.getNextNumber();
        nFrameForSRRF = (int) gd.getNextNumber();

        correctVibration = gd.getNextBoolean();

        calculateAVG = gd.getNextBoolean();
        calculateSTD = gd.getNextBoolean();

        getInterpolatedImage = gd.getNextBoolean();


        boolean showAdvancedSettings = gd.getNextBoolean();

        if (showAdvancedSettings) advancedSettingsGUI();


//        frameGap = (int) gd.getNextNumber();
//        chosenDeviceName = gd.getNextChoice();
//        maxMemoryGPU = (int) gd.getNextNumber();
//        blockSize = (int) gd.getNextNumber();
//
//        writeToDisk = gd.getNextBoolean();
//
//        // Calculate the parameters based on user inputs
//        if (nFrameForSRRF == 0) nFrameForSRRF = nSlices;
//        nFrameForSRRF = min(nSlices, nFrameForSRRF);
//        nSRRFframe = (int) ((float) (nSlices - nFrameForSRRF) / (float) frameGap) + 1;
//
//        if (frameGap == 0) frameGap = nFrameForSRRF;
//
//        int maxnFrameOnGPU = 0;
//        double[] memUsed = new double[2];
//        while (memUsed[0] < (double) maxMemoryGPU) {
//            maxnFrameOnGPU = maxnFrameOnGPU + 1;
//            memUsed = predictMemoryUsed(maxnFrameOnGPU);
//        }
//
//        maxnFrameOnGPU = maxnFrameOnGPU - 1;
//
//        if (maxnFrameOnGPU > 0) {
//            goodToGo = true;
//            nFrameOnGPU = (int) math.ceil((float) nFrameForSRRF / (float) maxnFrameOnGPU);
//            nFrameOnGPU = (int) math.ceil(((float) nFrameForSRRF / (float) nFrameOnGPU));
//            nFrameOnGPU = min(nFrameForSRRF, nFrameOnGPU);
//            memUsed = predictMemoryUsed(nFrameOnGPU);
//
//            IJ.showStatus("liveSRRF - Number of frames on device: " + (nFrameOnGPU) + " (" + Math.round(memUsed[0]) + " MB)");
//        } else {
//            memUsed = predictMemoryUsed(1);
//            IJ.showStatus("liveSRRF - Minimum device memory: " + Math.round(memUsed[0]) + "MB");
//        }
//
//        // Check for RAM on computer
//        if (memUsed[1] > IJ.maxMemory()) {
//            goodToGo = false;
//            IJ.showStatus("liveSRRF - Max RAM available exceeded: (" + (Math.round(memUsed[1])) + "MB vs. " + (Math.round(IJ.maxMemory())) + "MB)");
//        }
//
//        nGPUloadPerSRRFframe = (int) math.ceil((float) nFrameForSRRF / (float) nFrameOnGPU);
//
//        if (writeToDisk && !previousWriteToDisk) {
//            pathToDisk = IJ.getDirectory("");
//            if (pathToDisk == null) writeToDisk = false;
//        }
//
//        previousWriteToDisk = writeToDisk;

        return goodToGo;
    }


    // --- Predict memory usage in MB ---
    private double[] predictMemoryUsed(int nFrameOnGPU) {

        double[] memUsed = new double[2];

        // Memory on GPU ----
        memUsed[0] = 0;
        memUsed[0] += width * height * nFrameOnGPU; // clBufferPx
        memUsed[0] += nFrameOnGPU; // clBufferShiftX
        memUsed[0] += nFrameOnGPU; // clBufferShiftY
        memUsed[0] += width * height * nFrameOnGPU; // clBufferGx
        memUsed[0] += width * height * nFrameOnGPU; // clBufferGy
        memUsed[0] += 4 * width * height * nFrameOnGPU; // clBufferGxInt
        memUsed[0] += 4 * width * height * nFrameOnGPU; // clBufferGyInt
        memUsed[0] += 2 * width * height * magnification * magnification; // clBufferRGC
        memUsed[0] += width * height * magnification * magnification; // clBufferInt

        // Memory on CPU ----
        memUsed[1] = 0;
        memUsed[1] += width * height * nSlices; // clBufferPx
        memUsed[1] += 2 * nSRRFframe * width * height * magnification * magnification; // clBufferSRRF
        memUsed[1] += nSRRFframe * width * height * magnification * magnification; // clBufferInt

        memUsed[0] *= Float.SIZE / 8000000d;
        memUsed[1] *= Float.SIZE / 8000000d;
        // TODO: if allocated RAM to Fiji is exceeded, suggest using WriteToDisk !

        return memUsed;
    }


    // --- Calculate shift using Cross-correlation matrix ---
    private float[] calculateShift(ImageProcessor ipRef, ImageProcessor ipData) {

        FloatProcessor fpCCM = (FloatProcessor) calculateCrossCorrelationMap(ipRef, ipData, false);

        int windowSize = radiusCCM * 2 + 1;
        int xStart = fpCCM.getWidth() / 2 - radiusCCM;
        int yStart = fpCCM.getHeight() / 2 - radiusCCM;
        fpCCM.setRoi(xStart, yStart, windowSize, windowSize);
        fpCCM = (FloatProcessor) fpCCM.crop();

        double vMax = -Double.MAX_VALUE;
        double vMin = Double.MAX_VALUE;
        double xMax = 0;
        double yMax = 0;

        // first do coarse search for max
        for (int y = 1; y < windowSize - 1; y++) {
            for (int x = 1; x < windowSize - 1; x++) {
                double v = fpCCM.getf(x, y);
                if (v > vMax) {
                    vMax = v;
                    xMax = x;
                    yMax = y;
                }
                vMin = min(v, vMin);
            }
        }
        //System.out.println("xMax="+xMax+" yMax="+yMax);

        //vMax = -Double.MAX_VALUE;
        // do fine search for max
        for (double y = yMax; y < yMax + 1; y += 0.01) {
            for (double x = xMax; x < xMax + 1; x += 0.01) {
                double v = fpCCM.getBicubicInterpolatedPixel(x, y, fpCCM);
                if (v > vMax) {
                    vMax = v;
                    xMax = x;
                    yMax = y;
                }
            }
        }

        // recenter pixels
        float shiftX = (float) xMax - radiusCCM;
        float shiftY = (float) yMax - radiusCCM;

        if (impCCM == null) {
            impCCM = new ImagePlus("CCM Vibration Stabilisation", fpCCM);
            impCCM.show();
        }

        impCCM.setProcessor(fpCCM);
        impCCM.setRoi(new PointRoi(xMax + .5, yMax + .5));
        impCCM.setDisplayRange(vMin, vMax);

        return new float[]{shiftX, shiftY};
    }


    // -- Calculate shift Array using Cross-correlation matrix --
    private void calculateShiftArray(int indexStart) {

        imp.setSlice(indexStart);
        ImageProcessor ipRef = imp.getProcessor().duplicate();
        ImageProcessor ipData;

        for (int s = 0; s < nFrameForSRRF; s++) {

            // Check if user is cancelling calculation
            IJ.showProgress(s, nFrameForSRRF);
            if (IJ.escapePressed()) {
                liveSRRF.release();
                IJ.resetEscape();
                IJ.log("-------------------------------------");
                IJ.log("Reconstruction aborted by user.");
                return;
            }

            // Grab the new frame from the list
            imp.setSlice(s + indexStart);
            ipData = imp.getProcessor();

            // Estimate vibrations
            int id = prof.startTimer();
            float[] shift = calculateShift(ipRef, ipData);
            shiftX[s] = shift[0];
            shiftY[s] = shift[1];

            System.out.println("Frame=" + s + " shiftX=" + shiftX[s] + " shiftY=" + shiftY[s]);
            prof.recordTime("Drift Estimation", prof.endTimer(id));
        }

    }

    // -- Save user's last parameter set --
    private void savePreferences() {

        prefs.set("magnification", (float) magnification);
        prefs.set("fwhm", fwhm);
        prefs.set("nFrameForSRRF", nFrameForSRRF);
        prefs.set("sensitivity", sensitivity);
        prefs.set("correctVibration", correctVibration);

        prefs.set("calculateAVG", calculateAVG);
        prefs.set("calculateSTD", calculateSTD);
        prefs.set("getInterpolatedImage", getInterpolatedImage);

        prefs.set("frameGap", frameGap);
        prefs.set("chosenDeviceName", chosenDeviceName);
        prefs.set("maxMemoryGPU", maxMemoryGPU);
        prefs.set("blockSize", blockSize);

        prefs.save();

    }

    // -- Main GUI --

    private void mainGUI(){
        // Build GUI
        Font headerFont = new Font("Arial", Font.BOLD, 16);
        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("liveSRRF " + LiveSRRFVersion);
        gd.addMessage("-=-= SRRF parameters =-=-\n", headerFont);
        gd.addNumericField("Magnification (default: 5)", prefs.get("magnification", 5), 0);
        gd.addNumericField("Radius (pixels, default: 2)", prefs.get("fwhm", 2), 2);
        gd.addNumericField("Sensitivity (default: 1)", prefs.get("sensitivity", 1), 0);
        gd.addNumericField("# frames for SRRF (0 = auto)", prefs.get("nFrameForSRRF", 0), 0);
        gd.addCheckbox("Correct vibration", prefs.get("correctVibration", false));

        gd.addMessage("-=-= Reconstructions =-=-\n", headerFont);
        gd.addCheckbox("AVG reconstruction (default: on)", prefs.get("calculateAVG", true));
        gd.addCheckbox("STD reconstruction (default: off)", prefs.get("calculateSTD", false));
        gd.addCheckbox("Wide-field interpolation (default: off)", prefs.get("getInterpolatedImage", false));

        gd.addMessage("-=-= Advanced settings =-=-\n", headerFont);
        gd.addCheckbox("Show advanced settings", prefs.get("showAdvSettings", false));

        MyDialogListenerMainGUI dl = new MyDialogListenerMainGUI(); // this serves to estimate a few indicators such as RAM usage
        gd.addDialogListener(dl);
        gd.showDialog();

        // If the GUI was cancelled
        if (gd.wasCanceled()) {
            liveSRRF.release();
            return;
        }
    }

    // -- Advanced settings GUI --
    private void advancedSettingsGUI() {

        CLDevice[] allDevices = liveSRRF.checkDevices();

        // Initializing string for device choice
        String[] deviceNames = new String[allDevices.length + 1];
        deviceNames[0] = "Default device";

        for (int i = 1; i <= allDevices.length; i++) {
            deviceNames[i] = allDevices[i - 1].getName();
        }

        // Build GUI
        Font headerFont = new Font("Arial", Font.BOLD, 16);
        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("liveSRRF - Advanced settings");

        gd.addMessage("-=-= Rolling analysis =-=-\n", headerFont);
        gd.addNumericField("# frame gap between SR frame (0 = auto)", prefs.get("frameGap", 0), 0);
        gd.addMessage("Warning: Rolling analysis may lead to long computation times.");

        gd.addMessage("-=-= GPU/CPU processing =-=-\n", headerFont);
        gd.addChoice("Processing device", deviceNames, prefs.get("chosenDeviceName", "Default device"));
        gd.addNumericField("Maximum amount of memory on device (MB, default: 1000)", prefs.get("maxMemoryGPU", 500), 2);
        gd.addMessage("Giving liveSRRF access to a lot of memory speeds up the reconstruction\n" +
                "but may slow down the graphics card for your Minecraft game that you have \n" +
                "running in parallel.");

        gd.addNumericField("Analysis block size (default: 20000)", prefs.get("blockSize", 20000), 0);
        gd.addMessage("A large analysis block size will speed up the analysis but will use\n" +
                "more resources and may slow down your computer.");

        gd.addMessage("-=-= Write to disk =-=-\n", headerFont);
        gd.addCheckbox("Directly write to disk (default: off)", false);
        gd.addMessage("Writing directly to disk will slow down the reconstruction but \n" +
                "will allow for long time courses to be reconstructed without\n" +
                "exceeding RAM capacity.");

        MyDialogListenerAdvancedGUI dl = new MyDialogListenerAdvancedGUI(); // this serves to estimate a few indicators such as RAM usage
        gd.addDialogListener(dl);
        gd.showDialog();

        // If the GUI was cancelled
        if (gd.wasCanceled()) {
            liveSRRF.release();
            return;
        }

        // Get chosen device
        for (CLDevice allDevice : allDevices) {
            if (chosenDeviceName.equals(allDevice.getName())) chosenDevice = allDevice;
        }

    }



    //    --- Grab settings from main GUI ---
    private boolean grabSettingsAdvancedGUI(GenericDialog gd) {

        boolean goodToGo = false;

        frameGap = (int) gd.getNextNumber();
        chosenDeviceName = gd.getNextChoice();
        maxMemoryGPU = (int) gd.getNextNumber();
        blockSize = (int) gd.getNextNumber();

        writeToDisk = gd.getNextBoolean();

        // Calculate the parameters based on user inputs
        if (nFrameForSRRF == 0) nFrameForSRRF = nSlices;
        nFrameForSRRF = min(nSlices, nFrameForSRRF);
        nSRRFframe = (int) ((float) (nSlices - nFrameForSRRF) / (float) frameGap) + 1;

        if (frameGap == 0) frameGap = nFrameForSRRF;

        int maxnFrameOnGPU = 0;
        double[] memUsed = new double[2];
        while (memUsed[0] < (double) maxMemoryGPU) {
            maxnFrameOnGPU = maxnFrameOnGPU + 1;
            memUsed = predictMemoryUsed(maxnFrameOnGPU);
        }

        maxnFrameOnGPU = maxnFrameOnGPU - 1;

        if (maxnFrameOnGPU > 0) {
            goodToGo = true;
            nFrameOnGPU = (int) math.ceil((float) nFrameForSRRF / (float) maxnFrameOnGPU);
            nFrameOnGPU = (int) math.ceil(((float) nFrameForSRRF / (float) nFrameOnGPU));
            nFrameOnGPU = min(nFrameForSRRF, nFrameOnGPU);
            memUsed = predictMemoryUsed(nFrameOnGPU);

            IJ.showStatus("liveSRRF - Number of frames on device: " + (nFrameOnGPU) + " (" + Math.round(memUsed[0]) + " MB)");
        } else {
            memUsed = predictMemoryUsed(1);
            IJ.showStatus("liveSRRF - Minimum device memory: " + Math.round(memUsed[0]) + "MB");
        }

        // Check for RAM on computer
        if (memUsed[1] > IJ.maxMemory()) {
            goodToGo = false;
            IJ.showStatus("liveSRRF - Max RAM available exceeded: (" + (Math.round(memUsed[1])) + "MB vs. " + (Math.round(IJ.maxMemory())) + "MB)");
        }

        nGPUloadPerSRRFframe = (int) math.ceil((float) nFrameForSRRF / (float) nFrameOnGPU);

        if (writeToDisk && !previousWriteToDisk) {
            pathToDisk = IJ.getDirectory("");
            if (pathToDisk == null) writeToDisk = false;
        }

        previousWriteToDisk = writeToDisk;

        return goodToGo;
    }

}
