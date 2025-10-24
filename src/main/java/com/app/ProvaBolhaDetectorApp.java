package com.app;

import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.io.*;
import java.util.*;
import java.util.List;

public class ProvaBolhaDetectorApp extends Application {

    private TextField folderField;
    private TableView<RowResult> table;
    private ObservableList<RowResult> rows = FXCollections.observableArrayList();

    // Ajustáveis
    private static final double FILL_THRESHOLD = 0.38; // acima disso: considerada preenchida
    private static final int MIN_BLOB_AREA = 80;
    private static final int MAX_BLOB_AREA = 8000;
    private static final int TOLERANCIA_Y = 25;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Detector de Bolhas - Provas Escaneadas");

        Label lblFolder = new Label("Pasta com provas (PDF):");
        folderField = new TextField();
        folderField.setPrefWidth(500);
        Button btnBrowseFolder = new Button("Selecionar pasta");
        btnBrowseFolder.setOnAction(e -> chooseFolder(stage));

        HBox boxFolder = new HBox(8, folderField, btnBrowseFolder);
        boxFolder.setPadding(new Insets(5));

        Button btnProcess = new Button("Processar provas");
        btnProcess.setOnAction(e -> processarProvas());

        Button btnExport = new Button("Exportar Excel (.xlsx)");
        btnExport.setOnAction(e -> exportarExcel(stage));

        HBox controls = new HBox(10, btnProcess, btnExport);
        controls.setPadding(new Insets(10));

        table = new TableView<>();
        TableColumn<RowResult, String> cFile = new TableColumn<>("Arquivo");
        cFile.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().nomeArquivo));
        cFile.setPrefWidth(300);

        TableColumn<RowResult, Integer> cScore = new TableColumn<>("Total de questões");
        cScore.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().nota).asObject());
        cScore.setPrefWidth(120);

        TableColumn<RowResult, String> cDetail = new TableColumn<>("Respostas detectadas");
        cDetail.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().detalhe));
        cDetail.setPrefWidth(400);

        table.getColumns().addAll(cFile, cScore, cDetail);
        table.setItems(rows);

        VBox root = new VBox(10, boxFolder, controls, table);
        root.setPadding(new Insets(12));

        stage.setScene(new Scene(root, 840, 520));
        stage.show();
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Selecione a pasta com as provas");
        File dir = dc.showDialog(stage);
        if (dir != null) folderField.setText(dir.getAbsolutePath());
    }

    private void processarProvas() {
        rows.clear();
        String folderPath = folderField.getText().trim();

        if (folderPath.isEmpty()) {
            showAlert("Selecione a pasta com os PDFs antes de processar.");
            return;
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            showAlert("Pasta inválida.");
            return;
        }

        File[] arquivos = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".pdf"));
        if (arquivos == null || arquivos.length == 0) {
            showAlert("Nenhum PDF encontrado na pasta.");
            return;
        }

        for (File pdf : arquivos) {
            try {
                Map<Integer, String> respostas = processarPdfComoScaneado(pdf);
                String detalhe = gerarDetalheString(respostas);
                rows.add(new RowResult(pdf.getName(), respostas.size(), detalhe));
            } catch (Exception ex) {
                ex.printStackTrace();
                rows.add(new RowResult(pdf.getName(), 0, "Erro: " + ex.getMessage()));
            }
        }

        showAlert("Processamento concluído. " + arquivos.length + " arquivos processados.");
    }

    private Map<Integer, String> processarPdfComoScaneado(File pdf) throws IOException {
        Map<Integer, String> mapaRespostas = new TreeMap<>();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int numPages = document.getNumberOfPages();

            for (int p = 0; p < numPages; p++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(p, 150);
                List<Blob> blobs = detectarBlobs(pageImage);
                List<Blob> bolhas = filtrarBolhas(blobs);

                Map<Integer, List<Blob>> linhas = agruparPorLinha(bolhas, TOLERANCIA_Y);
                List<Integer> yKeys = new ArrayList<>(linhas.keySet());
                Collections.sort(yKeys);

                int questao = 1;
                for (int yBase : yKeys) {
                    List<Blob> linha = linhas.get(yBase);
                    linha.sort(Comparator.comparingDouble(b -> b.cx));

                    int count = Math.min(4, linha.size());
                    for (int i = 0; i < count; i++) {
                        Blob b = linha.get(i);
                        if (b.fillRatio >= FILL_THRESHOLD) {
                            mapaRespostas.put(questao, indiceParaLetra(i));
                            break;
                        }
                    }
                    questao++;
                }
            }
        }

        return mapaRespostas;
    }

    private String gerarDetalheString(Map<Integer, String> respostas) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : respostas.entrySet()) {
            sb.append(e.getKey()).append(":").append(e.getValue()).append("  ");
        }
        return sb.toString().trim();
    }

    private String indiceParaLetra(int i) {
        switch (i) {
            case 0: return "A";
            case 1: return "B";
            case 2: return "C";
            case 3: return "D";
            default: return "?";
        }
    }

    // --- Detecção de blobs ---
    private List<Blob> detectarBlobs(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[][] bin = new int[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                bin[y][x] = gray < 180 ? 1 : 0;
            }
        }

        boolean[][] vis = new boolean[h][w];
        List<Blob> blobs = new ArrayList<>();
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!vis[y][x] && bin[y][x] == 1) {
                    int minX = x, maxX = x, minY = y, maxY = y;
                    int area = 0;
                    Queue<Point> q = new ArrayDeque<>();
                    q.add(new Point(x, y));
                    vis[y][x] = true;

                    while (!q.isEmpty()) {
                        Point p = q.remove();
                        int px = p.x, py = p.y;
                        area++;
                        minX = Math.min(minX, px);
                        maxX = Math.max(maxX, px);
                        minY = Math.min(minY, py);
                        maxY = Math.max(maxY, py);

                        for (int k = 0; k < 4; k++) {
                            int nx = px + dx[k];
                            int ny = py + dy[k];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && !vis[ny][nx] && bin[ny][nx] == 1) {
                                vis[ny][nx] = true;
                                q.add(new Point(nx, ny));
                            }
                        }
                    }

                    int bboxW = maxX - minX + 1;
                    int bboxH = maxY - minY + 1;
                    double cx = minX + bboxW / 2.0;
                    double cy = minY + bboxH / 2.0;

                    Blob blob = new Blob(minX, minY, bboxW, bboxH, area, cx, cy);
                    blobs.add(blob);
                }
            }
        }

        for (Blob b : blobs) {
            int darkPixels = 0;
            for (int yy = b.y; yy < b.y + b.h; yy++) {
                if (yy < 0 || yy >= h) continue;
                for (int xx = b.x; xx < b.x + b.w; xx++) {
                    if (xx < 0 || xx >= w) continue;
                    int rgb = img.getRGB(xx, yy);
                    int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                    if (gray < 180) darkPixels++;
                }
            }
            b.fillRatio = darkPixels / (double) (b.w * b.h);
        }

        return blobs;
    }

    private List<Blob> filtrarBolhas(List<Blob> blobs) {
        List<Blob> out = new ArrayList<>();
        for (Blob b : blobs) {
            if (b.area < MIN_BLOB_AREA || b.area > MAX_BLOB_AREA) continue;
            double aspect = (double) b.w / b.h;
            if (aspect < 0.5 || aspect > 1.8) continue;
            out.add(b);
        }
        return out;
    }

    private Map<Integer, List<Blob>> agruparPorLinha(List<Blob> blobs, int toleranciaY) {
        Map<Integer, List<Blob>> linhas = new TreeMap<>();
        for (Blob b : blobs) {
            int yBase = (int) (Math.round(b.cy / toleranciaY) * toleranciaY);
            linhas.computeIfAbsent(yBase, k -> new ArrayList<>()).add(b);
        }
        return linhas;
    }

    // --- Exportação Excel ---
    private void exportarExcel(Stage stage) {
        if (rows.isEmpty()) {
            showAlert("Não há dados para exportar.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Salvar relatório Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        fc.setInitialFileName("relatorio_provas.xlsx");
        File out = fc.showSaveDialog(stage);
        if (out == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Resultados");
            int r = 0;
            Row header = sheet.createRow(r++);
            header.createCell(0).setCellValue("Arquivo");
            header.createCell(1).setCellValue("Total de questões");
            header.createCell(2).setCellValue("Respostas");

            for (RowResult rr : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(rr.nomeArquivo);
                row.createCell(1).setCellValue(rr.nota);
                row.createCell(2).setCellValue(rr.detalhe);
            }

            for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                workbook.write(fos);
            }

            showAlert("Excel salvo em: " + out.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Erro ao exportar Excel: " + ex.getMessage());
        }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.showAndWait();
    }

    // --- Helper classes ---
    public static class RowResult {
        final String nomeArquivo;
        final int nota;
        final String detalhe;

        public RowResult(String nomeArquivo, int nota, String detalhe) {
            this.nomeArquivo = nomeArquivo;
            this.nota = nota;
            this.detalhe = detalhe;
        }
    }

    private static class Blob {
        int x, y, w, h, area;
        double cx, cy, fillRatio;

        Blob(int x, int y, int w, int h, int area, double cx, double cy) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.area = area; this.cx = cx; this.cy = cy;
            this.fillRatio = 0.0;
        }
    }
}
