package com.example.imgaeclone;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.opencv.core.CvType.*;

public class ExposureFusion {

    private static final String TAG = "ExposureFusion";
    private static final Executor executor = Executors.newCachedThreadPool();
    private static boolean isRunning = false;

    public static void Process(Handler mHandler) {
        if (!isRunning) {
            isRunning = true;
            ProcessTask task = new ProcessTask(mHandler);
            executor.execute(task);
        } else {
            Log.d(TAG, "still running");
        }
    }

    private static class ProcessTask implements Runnable {
        private final Handler mHandler;

        public ProcessTask(Handler mHandler) {
            this.mHandler = mHandler;
        }

        @Override
        public void run() {
            Message message = new Message();
            message.what = 100;
            Bundle bundle = new Bundle();

            int numOfImages = images.size();
            if (numOfImages <= 0) {
                Log.e(TAG, "There is no image in list.");
                isRunning = false;
                bundle.putString("status", "failed");
                message.setData(bundle);
                this.mHandler.sendMessage(message);
                return;
            }
            int numOfRows = images.get(0).rows();
            int numOfCols = images.get(0).cols();
            for (Mat i : images) {
                if (i.rows() != numOfRows || i.cols() != numOfCols) {
                    Log.e(TAG, "The num of rows or cols is not equal.");
                    isRunning = false;
                    bundle.putString("status", "failed");
                    message.setData(bundle);
                    this.mHandler.sendMessage(message);
                    return;
                }
            }
            pyrDepth = pyrDepth <= 0 ? 1 : pyrDepth;

            List<Mat> weightMaps = computeWeight(images, wContrast, wSaturation, wExposedness);
            List<List<Mat>> pyrWeightMaps = new ArrayList<>();
            List<List<Mat>> pyrLaplacian = new ArrayList<>();
            // Calculate pyramid
            for (int i = 0; i < numOfImages; i++) {
                pyrWeightMaps.add(gPyramid(weightMaps.get(i), pyrDepth));
                pyrLaplacian.add(lPyramid(images.get(i), pyrDepth));
            }

            // weightMap * laplacian
            List<Mat> pyrFusion = new ArrayList<>();
            for (int i = 0; i < pyrDepth; i++) {
                Mat fusion = Mat.zeros(pyrLaplacian.get(0).get(i).size(), CV_32FC4);
                for (int k = 0; k < numOfImages; k++) {
                    // turn tempImage into CV_32FC4
                    Mat tempImage = pyrLaplacian.get(k).get(i).clone();
                    tempImage.convertTo(tempImage, CV_32FC4);

                    // convert tempWeight to CV_32FC4
                    Mat tempWeight = pyrWeightMaps.get(k).get(i).clone();
                    List<Mat> mergeWeight = new ArrayList<>();
                    for (int c = 0; c < 3; c++)
                        mergeWeight.add(tempWeight);
                    mergeWeight.add(Mat.ones(tempWeight.size(), CV_32FC1));
                    tempWeight = new Mat(tempWeight.size(), CV_32FC4);
                    Core.merge(mergeWeight, tempWeight);

                    // fusionValue = tempImage * tempWeight
                    Mat fusionValue = new Mat(tempImage.size(), CV_32FC4);
                    Core.multiply(tempImage, tempWeight, fusionValue);
                    Core.add(fusion, fusionValue, fusion);
                }
                pyrFusion.add(fusion);
            }

            result = pyrFusion.get(pyrDepth - 1);
            for (int i = pyrDepth - 2; i >= 0; i--) {
                Imgproc.pyrUp(result, result, pyrFusion.get(i).size());
                Core.add(result, pyrFusion.get(i), result);
            }

            brightness = 1 - brightness;
            brightness = (brightness >= 1) ? 1 : brightness;
            brightness = (brightness <= 0) ? 0.1 : brightness;
            // normalize
            result.convertTo(result, CV_8UC4, (1.0 / brightness));

            isRunning = false;
            bundle.putString("status", "success");
            message.setData(bundle);
            this.mHandler.sendMessage(message);
        }
    }

    private static List<Mat> images = new ArrayList<>();
    private static double wContrast = 1.0;
    private static double wSaturation = 1.0;
    private static double wExposedness = 1.0;
    private static int pyrDepth = 1;
    private static double brightness = 0.0;
    private static Mat result = new Mat();

    /**
    *
    * @param mImages input image list
    * @param mContrast control the weight of Contrast, normally is 1
    * @param mSaturation control the weight of Saturation, normally is 1
    * @param mExposedness control the weight of Exposedness, normally is 1
    * @param mPyrDepth control the times of gaussian and laplacian pyramid
    * @param mBrightness control the brightness of result image, range (0, 1]
    */
    public static void Init(List<Mat> mImages,
                            double mContrast,
                            double mSaturation,
                            double mExposedness,
                            int mPyrDepth,
                            double mBrightness) {
        images = mImages;
        wContrast = mContrast;
        wSaturation = mSaturation;
        wExposedness = mExposedness;
        pyrDepth = mPyrDepth;
        brightness = mBrightness;
    }

    public static Mat getResult() {
        return result;
    }

    private static List<Mat> computeWeight(List<Mat> images, double wC, double wS, double wE) {
        int numOfImages = images.size();
        int numOfRows = images.get(0).rows();
        int numOfCols = images.get(0).cols();
        List<Mat> weights = new ArrayList<>();
        Mat weightSum = Mat.zeros(images.get(0).size(), CV_32FC1);

        for (int i = 0; i < numOfImages; i++) {
            Mat imgFloat = new Mat(numOfRows, numOfCols, CV_32F);
            images.get(i).convertTo(imgFloat, CV_32F, (1.0 / 255.0));

            // contrast
            Mat contrast = computeContrast(imgFloat, wC);
            // saturation
            Mat saturation = computeSaturation(imgFloat, wS);
            // well-exposedness
            Mat wellExposedness = computeWellExposedness(imgFloat, wE);

            // calculate the weight of this image
            Mat weight = Mat.ones(numOfRows, numOfCols, CV_32FC1);
            Core.multiply(weight, contrast, weight);
            Core.multiply(weight, saturation, weight);
            Core.multiply(weight, wellExposedness, weight);
            // calculate the sum of weights
            Core.add(weightSum, weight, weightSum);
            // put the weight of this image into list
            weights.add(weight);
        }

        // add a small value to avoid divide by zero
        Core.add(weightSum, Scalar.all(1e-12), weightSum);
        // normalize
        for (int i = 0; i < numOfImages; i++)
            Core.divide(weights.get(i), weightSum, weights.get(i));

        return weights;
    }

    private static Mat computeContrast(Mat input, double wC) {
        int numOfRows = input.rows(), numOfCols = input.cols();

        Mat gray = new Mat(numOfRows, numOfCols, CV_32FC1);
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_RGB2GRAY);

        // low-pass filter, reduce noise
        Mat gaussian = gray.clone();
        Imgproc.GaussianBlur(gaussian, gaussian, new Size(3, 3), 0, 0, Core.BORDER_DEFAULT);
        Mat laplacian = new Mat();
        Imgproc.Laplacian(gaussian, laplacian, CV_32FC1, 3, 1, 0, Core.BORDER_DEFAULT );

        Mat contrast = new Mat(numOfRows, numOfCols, CV_32FC1);
        Core.absdiff(laplacian, Scalar.all(0), contrast);
        // (Cij,k)^wC
        Core.pow(contrast, wC, contrast);
        // fix zero value
        Core.add(contrast, Scalar.all(1), contrast);

        return contrast;
    }

    private static Mat computeSaturation(Mat input, double wS) {
        int numOfRows = input.rows(), numOfCols = input.cols();

        List<Mat> rgb = new ArrayList<>();
        Core.split(input, rgb);
        Mat meanValue = Mat.zeros(numOfRows, numOfCols, CV_32FC1);
        for (int c = 0; c < 3; c++)
            Core.add(meanValue, rgb.get(c), meanValue);
        Core.divide(meanValue, Scalar.all(3), meanValue);
        Mat stdValue = Mat.zeros(numOfRows, numOfCols, CV_32FC1);
        for (int c = 0; c < 3; c++) {
            Core.subtract(rgb.get(c), meanValue, rgb.get(c));
            Core.pow(rgb.get(c), 2, rgb.get(c));
            Core.add(stdValue, rgb.get(c), stdValue);
        }
        Core.divide(stdValue, Scalar.all(3), stdValue);

        Mat saturation = new Mat(numOfRows, numOfCols, CV_32FC1);
        Core.pow(stdValue, 0.5, saturation);
        // (Sij,k)^wS
        Core.pow(saturation, wS, saturation);
        // fix zero value
        Core.add(saturation, Scalar.all(1), saturation);

        return  saturation;
    }

    private static Mat computeWellExposedness(Mat input, double wE) {
        int numOfRows = input.rows(), numOfCols = input.cols();
        double muE = 0.5;
        double sigmaE = 0.2;

        Mat wellExposedness = new Mat(numOfRows, numOfCols, CV_32FC1);
        Imgproc.cvtColor(input, wellExposedness, Imgproc.COLOR_RGB2GRAY);
        // compute by gray value
        // there has another method is compute by rgb channels
        Core.subtract(wellExposedness, Scalar.all(muE), wellExposedness);
        Core.pow(wellExposedness, 2.0, wellExposedness);
        Core.divide(wellExposedness, Scalar.all(-2.0 * sigmaE * sigmaE), wellExposedness);
        Core.exp(wellExposedness, wellExposedness);
        // (Eij,k)^wE
        Core.pow(wellExposedness, wE, wellExposedness);
        // fix zero value
        Core.add(wellExposedness, Scalar.all(1), wellExposedness);

        return wellExposedness;
    }

    private static List<Mat> gPyramid(Mat image, int pyrDepth) {
        List<Mat> pyramid = new ArrayList<>();
        pyramid.add(image);
        for (int i = 0; i < pyrDepth - 1; i++) {
            Mat dst = new Mat();
            Imgproc.pyrDown(image, dst);
            pyramid.add(dst);
            image = dst.clone();
        }
        return pyramid;
    }

    private static List<Mat> lPyramid(Mat image, int pyrDepth) {
        List<Mat> pyramid = gPyramid(image, pyrDepth);
        for (int i = 0; i < pyrDepth - 1; i++) {
            Mat dst = new Mat();
            Imgproc.pyrUp(pyramid.get(i + 1), dst, pyramid.get(i).size());

            // Never calculate the alpha channel
            List<Mat> pyramidRGB = new ArrayList<>();
            List<Mat> dstRGB = new ArrayList<>();
            Core.split(pyramid.get(i), pyramidRGB);
            Core.split(dst, dstRGB);
            int numOfChannels = Math.min(3, Math.min(pyramidRGB.size(), dstRGB.size()));
            for (int c = 0; c < numOfChannels; c++) {
                // Negative value type
                pyramidRGB.get(c).convertTo(pyramidRGB.get(c), CV_32FC1);
                dstRGB.get(c).convertTo(dstRGB.get(c), CV_32FC1);
                Core.subtract(pyramidRGB.get(c), dstRGB.get(c), pyramidRGB.get(c));
            }
            pyramidRGB.get(pyramidRGB.size()-1).convertTo(pyramidRGB.get(pyramidRGB.size()-1), CV_32FC1);
            dstRGB.get(dstRGB.size()-1).convertTo(dstRGB.get(dstRGB.size()-1), CV_32FC1);

            Core.merge(pyramidRGB, pyramid.get(i));
        }
        return pyramid;
    }

}
