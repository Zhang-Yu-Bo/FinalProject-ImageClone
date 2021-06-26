package com.example.imgaeclone;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.*;

public class ExposureFusion {
    private static final String TAG = "ExposureFusion";

    /**
     *
     * @param images input image list
     * @param wContrast control the weight of Contrast, normally is 1
     * @param wSaturation control the weight of Saturation, normally is 1
     * @param wExposedness control the weight of Exposedness, normally is 1
     * @param pyrDepth control the times of gaussian and laplacian pyramid
     * @param brightness control the brightness of result image, range (0, 1]
     * @return return a image which is the fusion result of input image list
     */
    public static Mat process(List<Mat> images,
                              double wContrast,
                              double wSaturation,
                              double wExposedness,
                              int pyrDepth,
                              double brightness) {
        int numOfImages = images.size();
        if (numOfImages <= 0) {
            Log.e(TAG, "There is no image in list.");
            return null;
        }
        int numOfRows = images.get(0).rows();
        int numOfCols = images.get(0).cols();
        for (Mat i : images) {
            if (i.rows() != numOfRows || i.cols() != numOfCols) {
                Log.e(TAG, "The num of rows or cols is not equal.");
                return null;
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

        Mat result = pyrFusion.get(pyrDepth - 1);
        for (int i = pyrDepth - 2; i >= 0; i--) {
            Imgproc.pyrUp(result, result, pyrFusion.get(i).size());
            Core.add(result, pyrFusion.get(i), result);
        }

        brightness = 1 - brightness;
        brightness = (brightness > 1) ? 1 : brightness;
        brightness = (brightness <= 0) ? 1e-12 : brightness;
        // normalize
        result.convertTo(result, CV_8UC4, (1.0 / (pyrDepth * brightness)));

        return result;
    }

    private static List<Mat> computeWeight(List<Mat> images, double wC, double wS, double wE) {
        int numOfImages = images.size();
        int numOfRows = images.get(0).rows();
        int numOfCols = images.get(0).cols();
        List<Mat> weights = new ArrayList<>();
        Mat weightSum = Mat.zeros(images.get(0).size(), CV_32FC1);

        for (int i = 0; i < numOfImages; i++) {
            Mat tempImage = images.get(i).clone();
            Mat imgFloat = new Mat(numOfRows, numOfCols, CV_32F);
            tempImage.convertTo(imgFloat, CV_32F, (1.0 / 255.0));
            Mat gray = new Mat(numOfRows, numOfCols, CV_32FC1);
            Imgproc.cvtColor(imgFloat, gray, Imgproc.COLOR_RGB2GRAY);

            Mat contrast = new Mat(numOfRows, numOfCols, CV_32FC1);
            Mat saturation = new Mat(numOfRows, numOfCols, CV_32FC1);
            Mat wellExposedness = gray.clone();

            // contrast
            Mat gaussian = gray.clone();
            Imgproc.GaussianBlur(gaussian, gaussian, new Size(3, 3), 0, 0, Core.BORDER_DEFAULT);
            Mat laplacian = new Mat();
            Imgproc.Laplacian(gaussian, laplacian, CV_32FC1, 3, 1, 0, Core.BORDER_DEFAULT );
            Core.absdiff(laplacian, Scalar.all(0), contrast);
            // (Cij,k)^wC
            Core.pow(contrast, wC, contrast);
            // fix zero value
            Core.add(contrast, Scalar.all(1), contrast);

            // saturation
            List<Mat> rgb = new ArrayList<>();
            Core.split(imgFloat, rgb);
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
            Core.pow(stdValue, 0.5, saturation);
            // (Sij,k)^wS
            Core.pow(saturation, wS, saturation);
            // fix zero value
            Core.add(saturation, Scalar.all(1), saturation);

            // well-exposedness
            double muE = 0.5;
            double sigmaE = 0.2;
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

    private static List<Mat> gPyramid(Mat image, int pyrDepth) {
        List<Mat> pyramid = new ArrayList<>();
        pyramid.add(image);
        for (int i = 0; i < pyrDepth; i++) {
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
            pyramid.add(dst);
        }
        return pyramid;
    }

}
