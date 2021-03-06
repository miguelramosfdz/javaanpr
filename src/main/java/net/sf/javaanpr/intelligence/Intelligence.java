/*
------------------------------------------------------------------------
JavaANPR - Automatic Number Plate Recognition System for Java
------------------------------------------------------------------------

This file is a part of the JavaANPR, licensed under the terms of the
Educational Community License

Copyright (c) 2006-2007 Ondrej Martinsky. All rights reserved

This Original Work, including software, source code, documents, or
other related items, is being provided by the copyright holder(s)
subject to the terms of the Educational Community License. By
obtaining, using and/or copying this Original Work, you agree that you
have read, understand, and will comply with the following terms and
conditions of the Educational Community License:

Permission to use, copy, modify, merge, publish, distribute, and
sublicense this Original Work and its documentation, with or without
modification, for any purpose, and without fee or royalty to the
copyright holder(s) is hereby granted, provided that you include the
following on ALL copies of the Original Work or portions thereof,
including modifications or derivatives, that you make:

# The full text of the Educational Community License in a location
viewable to users of the redistributed or derivative work.

# Any pre-existing intellectual property disclaimers, notices, or terms
and conditions.

# Notice of any changes or modifications to the Original Work,
including the date the changes were made.

# Any modifications of the Original Work must be distributed in such a
manner as to avoid any confusion with the Original Work of the
copyright holders.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

The name and trademarks of copyright holder(s) may NOT be used in
advertising or publicity pertaining to the Original or Derivative Works
without specific, written prior permission. Title to copyright in the
Original Work and any associated documentation will at all times remain
with the copyright holders.

If you want to alter upon this work, you MUST attribute it in
a) all source files
b) on every place, where is the copyright of derivated work
exactly by the following label :

---- label begin ----
This work is a derivate of the JavaANPR. JavaANPR is a intellectual
property of Ondrej Martinsky. Please visit http://javaanpr.sourceforge.net
for more info about JavaANPR.
----  label end  ----

------------------------------------------------------------------------
                                         http://javaanpr.sourceforge.net
------------------------------------------------------------------------
 */

package net.sf.javaanpr.intelligence;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import net.sf.javaanpr.configurator.Configurator;
import net.sf.javaanpr.gui.TimeMeter;
import net.sf.javaanpr.imageanalysis.Band;
import net.sf.javaanpr.imageanalysis.CarSnapshot;
import net.sf.javaanpr.imageanalysis.Char;
import net.sf.javaanpr.imageanalysis.HoughTransformation;
import net.sf.javaanpr.imageanalysis.Photo;
import net.sf.javaanpr.imageanalysis.Plate;
import net.sf.javaanpr.jar.Main;
import net.sf.javaanpr.recognizer.CharacterRecognizer;
import net.sf.javaanpr.recognizer.CharacterRecognizer.RecognizedChar;
import net.sf.javaanpr.recognizer.KnnPatternClassificator;
import net.sf.javaanpr.recognizer.NeuralPatternClassificator;

import org.xml.sax.SAXException;

public class Intelligence {
    private static long lastProcessDuration = 0; // trvanie posledneho procesu v ms
    private static Configurator configurator = Configurator.getConfigurator();

    public static CharacterRecognizer chrRecog;
    public static Parser parser;

    public Intelligence() throws ParserConfigurationException, SAXException, IOException {

        int classification_method = configurator.getIntProperty("intelligence_classification_method");

        if (classification_method == 0) {
            chrRecog = new KnnPatternClassificator();
        } else {
            chrRecog = new NeuralPatternClassificator();
        }

        parser = new Parser();
    }

    /**
     * Vrati ako dlho v ms trvalo posledne rozpoznaavnie
     *
     * @return last process duration in milliseconds
     */
    public long lastProcessDuration() {
        return lastProcessDuration;
    }

    public String recognizeWithReport(CarSnapshot carSnapshot) throws IllegalArgumentException, IOException {
        final boolean enableReportGeneration = true;

        TimeMeter time = new TimeMeter();
        int syntaxAnalysisMode = configurator.getIntProperty("intelligence_syntaxanalysis");
        int skewDetectionMode = configurator.getIntProperty("intelligence_skewdetection");

        if (enableReportGeneration) {
            Main.rg.insertText("<h1>Automatic Number Plate Recognition Report</h1>");
            Main.rg.insertText("<span>Image width: " + carSnapshot.getWidth() + " px</span>");
            Main.rg.insertText("<span>Image height: " + carSnapshot.getHeight() + " px</span>");

            Main.rg.insertText("<h2>Vertical and Horizontal plate projection</h2>");

            Main.rg.insertImage(carSnapshot.renderGraph(), "snapshotgraph", 0, 0);
            Main.rg.insertImage(carSnapshot.getBiWithAxes(), "snapshot", 0, 0);
        }

        for (Band b : carSnapshot.getBands()) { // doporucene 3

            if (enableReportGeneration) {
                Main.rg.insertText("<div class='bandtxt'><h4>Band<br></h4>");
                Main.rg.insertImage(b.getBi(), "bandsmall", 250, 30);
                Main.rg.insertText("<span>Band width : " + b.getWidth() + " px</span>");
                Main.rg.insertText("<span>Band height : " + b.getHeight() + " px</span>");
                Main.rg.insertText("</div>");
            }

            for (Plate plate : b.getPlates()) {// doporucene 3

                if (enableReportGeneration) {
                    Main.rg.insertText("<div class='platetxt'><h4>Plate<br></h4>");
                    Main.rg.insertImage(plate.getBi(), "platesmall", 120, 30);
                    Main.rg.insertText("<span>Plate width : " + plate.getWidth() + " px</span>");
                    Main.rg.insertText("<span>Plate height : " + plate.getHeight() + " px</span>");
                    Main.rg.insertText("</div>");
                }

                // SKEW-RELATED
                Plate notNormalizedCopy;
                BufferedImage renderedHoughTransform;
                HoughTransformation hough = null;

                /*
                 * detekcia sa robi bud: 1. kvoli report generatoru 2. kvoli korekcii
                 */
                if (enableReportGeneration) {// || (skewDetectionMode != 0)) {
                    notNormalizedCopy = plate.clone();
                    notNormalizedCopy.horizontalEdgeDetector(notNormalizedCopy.getBi());
                    hough = notNormalizedCopy.getHoughTransformation();
                    renderedHoughTransform = hough.render(HoughTransformation.RENDER_ALL, HoughTransformation.COLOR_BW);
                }
                if (skewDetectionMode != 0) { // korekcia sa robi iba ak je
                                              // zapnuta
                    AffineTransform shearTransform = AffineTransform.getShearInstance(0, -(double) hough.dy / hough.dx);
                    BufferedImage core = Photo.createBlankBi(plate.getBi());
                    core.createGraphics().drawRenderedImage(plate.getBi(), shearTransform);
                    plate = new Plate(core);
                }

                plate.normalize();

                float plateWHratio = (float) plate.getWidth() / (float) plate.getHeight();
                if ((plateWHratio < configurator.getDoubleProperty("intelligence_minPlateWidthHeightRatio"))
                    || (plateWHratio > configurator.getDoubleProperty("intelligence_maxPlateWidthHeightRatio"))) {
                    continue;
                }

                Vector<Char> chars = plate.getChars();

                // heuristicka analyza znacky z pohladu uniformity a poctu
                // pismen :
                // Recognizer.configurator.getIntProperty("intelligence_minimumChars")
                if ((chars.size() < configurator.getIntProperty("intelligence_minimumChars"))
                    || (chars.size() > configurator.getIntProperty("intelligence_maximumChars"))) {
                    continue;
                }

                if (plate.getCharsWidthDispersion(chars) > configurator
                    .getDoubleProperty("intelligence_maxCharWidthDispersion")) {
                    continue;
                }

                /* ZNACKA PRIJATA, ZACINA NORMALIZACIA A HEURISTIKA PISMEN */

                if (enableReportGeneration) {
                    Main.rg.insertText("<h2>Detected band</h2>");
                    Main.rg.insertImage(b.getBiWithAxes(), "band", 0, 0);
                    Main.rg.insertImage(b.renderGraph(), "bandgraph", 0, 0);
                    Main.rg.insertText("<h2>Detected plate</h2>");
                    Plate plateCopy = plate.clone();
                    plateCopy.linearResize(450, 90);
                    Main.rg.insertImage(plateCopy.getBiWithAxes(), "plate", 0, 0);
                    Main.rg.insertImage(plateCopy.renderGraph(), "plategraph", 0, 0);
                }

                // SKEW-RELATED
                if (enableReportGeneration) {
                    Main.rg.insertText("<h2>Skew detection</h2>");
                    // Main.rg.insertImage(notNormalizedCopy.getBi());
                    Main.rg.insertImage(notNormalizedCopy.getBi(), "skewimage", 0, 0);
                    Main.rg.insertImage(renderedHoughTransform, "skewtransform", 0, 0);
                    Main.rg.insertText("Detected skew angle : <b>" + hough.angle + "</b>");
                }

                RecognizedPlate recognizedPlate = new RecognizedPlate();

                if (enableReportGeneration) {
                    Main.rg.insertText("<h2>Character segmentation</h2>");
                    Main.rg.insertText("<div class='charsegment'>");
                    for (Char chr : chars) {
                        Main.rg.insertImage(Photo.linearResizeBi(chr.getBi(), 70, 100), "", 0, 0);
                    }
                    Main.rg.insertText("</div>");
                }

                for (Char chr : chars) {
                    chr.normalize();
                }

                float averageHeight = plate.getAveragePieceHeight(chars);
                float averageContrast = plate.getAveragePieceContrast(chars);
                float averageBrightness = plate.getAveragePieceBrightness(chars);
                float averageHue = plate.getAveragePieceHue(chars);
                float averageSaturation = plate.getAveragePieceSaturation(chars);

                for (Char chr : chars) {
                    // heuristicka analyza jednotlivych pismen
                    boolean ok = true;
                    String errorFlags = "";

                    // pri normalizovanom pisme musime uvazovat pomer
                    float widthHeightRatio = (chr.pieceWidth);
                    widthHeightRatio /= (chr.pieceHeight);

                    if ((widthHeightRatio < configurator.getDoubleProperty("intelligence_minCharWidthHeightRatio"))
                        || (widthHeightRatio > configurator
                            .getDoubleProperty("intelligence_maxCharWidthHeightRatio"))) {
                        errorFlags += "WHR ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }

                    if (((chr.positionInPlate.x1 < 2) || (chr.positionInPlate.x2 > (plate.getWidth() - 1)))
                        && (widthHeightRatio < 0.12)) {
                        errorFlags += "POS ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }

                    // float similarityCost = rc.getSimilarityCost();

                    float contrastCost = Math.abs(chr.statisticContrast - averageContrast);
                    float brightnessCost = Math.abs(chr.statisticAverageBrightness - averageBrightness);
                    float hueCost = Math.abs(chr.statisticAverageHue - averageHue);
                    float saturationCost = Math.abs(chr.statisticAverageSaturation - averageSaturation);
                    float heightCost = (chr.pieceHeight - averageHeight) / averageHeight;

                    if (brightnessCost > configurator.getDoubleProperty("intelligence_maxBrightnessCostDispersion")) {
                        errorFlags += "BRI ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }
                    if (contrastCost > configurator.getDoubleProperty("intelligence_maxContrastCostDispersion")) {
                        errorFlags += "CON ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }
                    if (hueCost > configurator.getDoubleProperty("intelligence_maxHueCostDispersion")) {
                        errorFlags += "HUE ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }
                    if (saturationCost > configurator.getDoubleProperty("intelligence_maxSaturationCostDispersion")) {
                        errorFlags += "SAT ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }
                    if (heightCost < -configurator.getDoubleProperty("intelligence_maxHeightCostDispersion")) {
                        errorFlags += "HEI ";
                        ok = false;
                        if (!enableReportGeneration) {
                            continue;
                        }
                    }

                    float similarityCost = 0;
                    RecognizedChar rc = null;
                    if (ok) {
                        rc = chrRecog.recognize(chr);
                        similarityCost = rc.getPatterns().elementAt(0).getCost();

                        if (similarityCost > configurator.getDoubleProperty("intelligence_maxSimilarityCostDispersion")) {
                            errorFlags += "NEU ";
                            ok = false;
                            if (!enableReportGeneration) {
                                continue;
                            }
                        }

                    }

                    if (ok) {
                        recognizedPlate.addChar(rc);
                    } else {
                    }

                    if (enableReportGeneration) {
                        Main.rg.insertText("<div class='heuristictable'>");
                        Main.rg.insertImage(Photo.linearResizeBi(chr.getBi(), chr.getWidth() * 2, chr.getHeight() * 2),
                            "skeleton", 0, 0);
                        Main.rg.insertText("<span class='name'>WHR</span><span class='value'>" + widthHeightRatio
                            + "</span>");
                        Main.rg.insertText("<span class='name'>HEI</span><span class='value'>" + heightCost + "</span>");
                        Main.rg.insertText("<span class='name'>NEU</span><span class='value'>" + similarityCost
                            + "</span>");
                        Main.rg.insertText("<span class='name'>CON</span><span class='value'>" + contrastCost
                            + "</span>");
                        Main.rg.insertText("<span class='name'>BRI</span><span class='value'>" + brightnessCost
                            + "</span>");
                        Main.rg.insertText("<span class='name'>HUE</span><span class='value'>" + hueCost + "</span>");
                        Main.rg.insertText("<span class='name'>SAT</span><span class='value'>" + saturationCost
                            + "</span>");
                        Main.rg.insertText("</table>");
                        if (errorFlags.length() != 0) {
                            Main.rg.insertText("<span class='errflags'>" + errorFlags + "</span>");
                        }
                        Main.rg.insertText("</div>");
                    }
                } // end for each char

                // nasledujuci riadok zabezpeci spracovanie dalsieho kandidata
                // na znacku, v pripade ze charrecognizingu je prilis malo
                // rozpoznanych pismen
                if (recognizedPlate.chars.size() < configurator.getIntProperty("intelligence_minimumChars")) {
                    continue;
                }

                lastProcessDuration = time.getTime();
                String parsedOutput = Intelligence.parser.parse(recognizedPlate, syntaxAnalysisMode);

                if (enableReportGeneration) {
                    Main.rg.insertText("<span class='recognized'>");
                    Main.rg.insertText("Recognized plate : " + parsedOutput);
                    Main.rg.insertText("</span>");
                }

                return parsedOutput;

            } // end for each plate

        }

        lastProcessDuration = time.getTime();
        // return new String("not available yet ;-)");
        return null;
    }

    public String recognize(CarSnapshot carSnapshot) {
        TimeMeter time = new TimeMeter();
        int syntaxAnalysisMode = configurator.getIntProperty("intelligence_syntaxanalysis");
        int skewDetectionMode = configurator.getIntProperty("intelligence_skewdetection");

        for (Band b : carSnapshot.getBands()) { // doporucene 3

            for (Plate plate : b.getPlates()) {// doporucene 3

                // SKEW-RELATED
                Plate notNormalizedCopy;

                @SuppressWarnings("unused")
                BufferedImage renderedHoughTransform = null;
                HoughTransformation hough = null;
                if (skewDetectionMode != 0) { // detekcia
                                              // sa
                                              // robi
                                              // but
                                              // 1)
                                              // koli
                                              // report
                                              // generatoru
                                              // 2)
                                              // koli
                                              // korekcii
                    notNormalizedCopy = plate.clone();
                    notNormalizedCopy.horizontalEdgeDetector(notNormalizedCopy.getBi());
                    hough = notNormalizedCopy.getHoughTransformation();
                    renderedHoughTransform = hough.render(HoughTransformation.RENDER_ALL, HoughTransformation.COLOR_BW);
                }
                if (skewDetectionMode != 0) { // korekcia sa robi iba ak je
                                              // zapnuta
                    AffineTransform shearTransform = AffineTransform.getShearInstance(0, -(double) hough.dy / hough.dx);
                    BufferedImage core = Photo.createBlankBi(plate.getBi());
                    core.createGraphics().drawRenderedImage(plate.getBi(), shearTransform);
                    plate = new Plate(core);
                }

                plate.normalize();

                float plateWHratio = (float) plate.getWidth() / (float) plate.getHeight();
                if ((plateWHratio < configurator.getDoubleProperty("intelligence_minPlateWidthHeightRatio"))
                    || (plateWHratio > configurator.getDoubleProperty("intelligence_maxPlateWidthHeightRatio"))) {
                    continue;
                }

                Vector<Char> chars = plate.getChars();

                // heuristicka analyza znacky z pohladu uniformity a poctu
                // pismen :
                // Recognizer.configurator.getIntProperty("intelligence_minimumChars")
                if ((chars.size() < configurator.getIntProperty("intelligence_minimumChars"))
                    || (chars.size() > configurator.getIntProperty("intelligence_maximumChars"))) {
                    continue;
                }

                if (plate.getCharsWidthDispersion(chars) > configurator
                    .getDoubleProperty("intelligence_maxCharWidthDispersion")) {
                    continue;
                }

                /* ZNACKA PRIJATA, ZACINA NORMALIZACIA A HEURISTIKA PISMEN */

                RecognizedPlate recognizedPlate = new RecognizedPlate();

                for (Char chr : chars) {
                    chr.normalize();
                }

                float averageHeight = plate.getAveragePieceHeight(chars);
                float averageContrast = plate.getAveragePieceContrast(chars);
                float averageBrightness = plate.getAveragePieceBrightness(chars);
                float averageHue = plate.getAveragePieceHue(chars);
                float averageSaturation = plate.getAveragePieceSaturation(chars);

                for (Char chr : chars) {
                    // heuristicka analyza jednotlivych pismen
                    boolean ok = true;

                    @SuppressWarnings("unused")
                    String errorFlags = "";

                    // pri normalizovanom pisme musime uvazovat pomer
                    float widthHeightRatio = (chr.pieceWidth);
                    widthHeightRatio /= (chr.pieceHeight);

                    if ((widthHeightRatio < configurator.getDoubleProperty("intelligence_minCharWidthHeightRatio"))
                        || (widthHeightRatio > configurator
                            .getDoubleProperty("intelligence_maxCharWidthHeightRatio"))) {
                        errorFlags += "WHR ";
                        ok = false;
                        continue;
                    }

                    if (((chr.positionInPlate.x1 < 2) || (chr.positionInPlate.x2 > (plate.getWidth() - 1)))
                        && (widthHeightRatio < 0.12)) {
                        errorFlags += "POS ";
                        ok = false;
                        continue;
                    }

                    // float similarityCost = rc.getSimilarityCost();

                    float contrastCost = Math.abs(chr.statisticContrast - averageContrast);
                    float brightnessCost = Math.abs(chr.statisticAverageBrightness - averageBrightness);
                    float hueCost = Math.abs(chr.statisticAverageHue - averageHue);
                    float saturationCost = Math.abs(chr.statisticAverageSaturation - averageSaturation);
                    float heightCost = (chr.pieceHeight - averageHeight) / averageHeight;

                    if (brightnessCost > configurator.getDoubleProperty("intelligence_maxBrightnessCostDispersion")) {
                        errorFlags += "BRI ";
                        ok = false;
                        continue;
                    }
                    if (contrastCost > configurator.getDoubleProperty("intelligence_maxContrastCostDispersion")) {
                        errorFlags += "CON ";
                        ok = false;
                        continue;
                    }
                    if (hueCost > configurator.getDoubleProperty("intelligence_maxHueCostDispersion")) {
                        errorFlags += "HUE ";
                        ok = false;
                        continue;
                    }
                    if (saturationCost > configurator.getDoubleProperty("intelligence_maxSaturationCostDispersion")) {
                        errorFlags += "SAT ";
                        ok = false;
                        continue;
                    }
                    if (heightCost < -configurator.getDoubleProperty("intelligence_maxHeightCostDispersion")) {
                        errorFlags += "HEI ";
                        ok = false;
                        continue;
                    }

                    float similarityCost;
                    RecognizedChar rc = null;
                    if (ok) {
                        rc = chrRecog.recognize(chr);
                        similarityCost = rc.getPatterns().elementAt(0).getCost();

                        if (similarityCost > configurator.getDoubleProperty("intelligence_maxSimilarityCostDispersion")) {
                            errorFlags += "NEU ";
                            ok = false;
                            continue;
                        }

                    }

                    if (ok) {
                        recognizedPlate.addChar(rc);
                    }
                } // end for each char

                // nasledujuci riadok zabezpeci spracovanie dalsieho kandidata
                // na znacku, v pripade ze charrecognizingu je prilis malo
                // rozpoznanych pismen
                if (recognizedPlate.chars.size() < configurator.getIntProperty("intelligence_minimumChars")) {
                    continue;
                }

                lastProcessDuration = time.getTime();
                return Intelligence.parser.parse(recognizedPlate, syntaxAnalysisMode);

            } // end for each plate

        }

        lastProcessDuration = time.getTime();
        // return new String("not available yet ;-)");
        return null;
    }
}