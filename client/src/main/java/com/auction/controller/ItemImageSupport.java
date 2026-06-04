package com.auction.controller;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

final class ItemImageSupport {

  private static final int MAX_IMAGE_EDGE_PX = 1280;
  private static final float IMAGE_JPEG_QUALITY = 0.82f;

  private ItemImageSupport() {}

  static Image toImage(String dataUrl) {
    try {
      String base64 = dataUrl;
      int comma = dataUrl.indexOf(',');
      if (comma >= 0) {
        base64 = dataUrl.substring(comma + 1);
      }
      byte[] bytes = Base64.getDecoder().decode(base64);
      return new Image(new ByteArrayInputStream(bytes));
    } catch (RuntimeException e) {
      // Invalid image payloads render as empty thumbnails, matching the previous UI behavior.
      return null;
    }
  }

  static String imageDataUrl(File file) throws IOException {
    BufferedImage source = ImageIO.read(file);
    if (source == null) {
      throw new IOException("Unsupported image file.");
    }

    BufferedImage output = scaledImage(source);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(IMAGE_JPEG_QUALITY);
      writer.write(null, new IIOImage(output, null, null), param);
    } finally {
      writer.dispose();
    }

    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
  }

  static void openImageViewer(String imageData, String stylesheetUrl) {
    Image image = toImage(imageData);
    if (image == null) {
      return;
    }

    ImageView viewer = new ImageView(image);
    viewer.setPreserveRatio(true);
    viewer.setSmooth(true);

    double[] zoom = {1.0};
    Runnable applyZoom =
        () -> {
          viewer.setFitWidth(Math.max(240, image.getWidth() * zoom[0]));
          viewer.setFitHeight(Math.max(180, image.getHeight() * zoom[0]));
        };

    Button zoomOut = new Button("Zoom Out");
    Button zoomIn = new Button("Zoom In");
    Button reset = new Button("Reset");
    zoomOut.setOnAction(
        e -> {
          zoom[0] = Math.max(0.25, zoom[0] / 1.25);
          applyZoom.run();
        });
    zoomIn.setOnAction(
        e -> {
          zoom[0] = Math.min(5.0, zoom[0] * 1.25);
          applyZoom.run();
        });
    reset.setOnAction(
        e -> {
          zoom[0] = 1.0;
          applyZoom.run();
        });
    applyZoom.run();

    HBox toolbar = new HBox(8, zoomOut, reset, zoomIn);
    toolbar.setAlignment(javafx.geometry.Pos.CENTER);
    toolbar.setPadding(new Insets(10));

    ScrollPane scroller = new ScrollPane(viewer);
    scroller.setPannable(true);
    scroller.setFitToWidth(false);
    scroller.setFitToHeight(false);
    scroller.setStyle("-fx-background-color: #0f172a;");

    VBox root = new VBox(toolbar, scroller);
    VBox.setVgrow(scroller, Priority.ALWAYS);

    Stage imageStage = new Stage();
    imageStage.setTitle("Item Image");
    Scene scene = new Scene(root, 1000, 760);
    if (stylesheetUrl != null && !stylesheetUrl.isBlank()) {
      scene.getStylesheets().add(stylesheetUrl);
    }
    imageStage.setScene(scene);
    imageStage.show();
  }

  private static BufferedImage scaledImage(BufferedImage source) {
    int width = source.getWidth();
    int height = source.getHeight();
    double scale = Math.min(1.0, (double) MAX_IMAGE_EDGE_PX / Math.max(width, height));
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));

    BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = output.createGraphics();
    try {
      graphics.setColor(java.awt.Color.WHITE);
      graphics.fillRect(0, 0, targetWidth, targetHeight);
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
    } finally {
      graphics.dispose();
    }
    return output;
  }
}
