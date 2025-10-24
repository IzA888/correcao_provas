module com.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires org.apache.poi.ooxml;
    requires org.apache.pdfbox;
    requires org.bytedeco.javacpp;

    opens com.app to javafx.fxml;
    exports com.app;
}
