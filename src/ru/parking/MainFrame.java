package ru.parking;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileInputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class MainFrame extends JFrame {
    private JTable spotTable, historyTable;
    private DefaultTableModel spotModel, historyModel;
    private JTextField searchField;
    private JComboBox<String> categoryCombo;
    private JLabel statsLabel, timeLabel;
    private TableRowSorter<DefaultTableModel> sorter;

    public MainFrame() {
        String dbTypeDisp = "Unknown";
        try (FileInputStream in = new FileInputStream("config.properties")) {
            Properties p = new Properties(); p.load(in);
            dbTypeDisp = p.getProperty("current.db").toUpperCase();
        } catch (Exception e) {}
        
        setTitle("SmartParking v7.7 [БАЗА: " + dbTypeDisp + "] - Панель Администратора");
        setSize(1300, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(230, 235, 240));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY));

        JPanel filtersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        filtersPanel.setOpaque(false);
        searchField = new JTextField(12);
        categoryCombo = new JComboBox<>();
        categoryCombo.addItem("Все категории");
        
        filtersPanel.add(new JLabel("🔍 Поиск:"));
        filtersPanel.add(searchField);
        filtersPanel.add(new JLabel("📍 Категория:"));
        filtersPanel.add(categoryCombo);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setOpaque(false);
        statsLabel = new JLabel("Загрузка статистики...  ", SwingConstants.RIGHT);
        statsLabel.setFont(new Font("Arial", Font.BOLD, 13));
        timeLabel = new JLabel("", SwingConstants.RIGHT);
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 15));
        infoPanel.add(statsLabel);
        infoPanel.add(timeLabel);

        headerPanel.add(filtersPanel, BorderLayout.WEST);
        headerPanel.add(infoPanel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        spotModel = new DefaultTableModel(new String[]{"№", "Место", "Тип", "Статус", "Цена/час", "DB_ID"}, 0);
        spotTable = new JTable(spotModel);
        spotTable.removeColumn(spotTable.getColumnModel().getColumn(5));
        setupSpotRenderer();
        sorter = new TableRowSorter<>(spotModel);
        spotTable.setRowSorter(sorter);

        historyModel = new DefaultTableModel(new String[]{"Госномер", "Марка", "Место", "Тариф", "Заезд", "Выезд", "Оплата"}, 0);
        historyTable = new JTable(historyModel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JScrollPane sp1 = new JScrollPane(spotTable);
        sp1.setBorder(BorderFactory.createTitledBorder(" КАРТА ПАРКОВКИ (Сортировка: Тип -> Номер) "));
        splitPane.setTopComponent(sp1);
        JScrollPane sp2 = new JScrollPane(historyTable);
        sp2.setBorder(BorderFactory.createTitledBorder(" ЖУРНАЛ ПОСЕЩЕНИЙ "));
        splitPane.setBottomComponent(sp2);
        splitPane.setDividerLocation(380);
        add(splitPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(8, 1, 5, 10));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JButton btnEntry = new JButton("РЕГИСТРАЦИЯ ВЪЕЗДА");
        JButton btnExit = new JButton("ОФОРМИТЬ ВЫЕЗД");
        JButton btnRefresh = new JButton("ОБНОВИТЬ ВСЁ");
        btnEntry.setBackground(new Color(200, 240, 200));
        btnExit.setBackground(new Color(240, 200, 200));
        btnPanel.add(btnEntry); btnPanel.add(btnExit); btnPanel.add(new JSeparator()); btnPanel.add(btnRefresh);
        add(btnPanel, BorderLayout.EAST);

        new Timer(1000, e -> timeLabel.setText(new SimpleDateFormat("dd.MM.yyyy  HH:mm:ss ").format(new Date()))).start();

        searchField.addCaretListener(e -> applyFilters());
        categoryCombo.addActionListener(e -> applyFilters());
        btnRefresh.addActionListener(e -> refreshAll());
        btnEntry.addActionListener(e -> handleEntry());
        btnExit.addActionListener(e -> handleExit());

        refreshAll();
        loadCategories();
        setLocationRelativeTo(null);
    }

    private void applyFilters() {
        String text = searchField.getText();
        String cat = (String) categoryCombo.getSelectedItem();
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        if (text != null && !text.trim().isEmpty()) filters.add(RowFilter.regexFilter("(?i)" + text));
        if (cat != null && !cat.equals("Все категории")) filters.add(RowFilter.regexFilter("^" + cat + "$", 2));
        if (filters.isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.andFilter(filters));
    }

    private void loadCategories() {
        categoryCombo.removeAllItems();
        categoryCombo.addItem("Все категории");
        try (Connection conn = DBConnection.getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT type_name FROM spot_types ORDER BY type_name")) {
            while (rs.next()) categoryCombo.addItem(rs.getString("type_name"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void refreshAll() { loadSpots(); loadHistory(); }

    private void setupSpotRenderer() {
        spotTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isS, boolean hasF, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, val, isS, hasF, row, col);
                if ("ЗАНЯТО".equals(val)) c.setForeground(Color.RED);
                else c.setForeground(new Color(0, 130, 0));
                return c;
            }
        });
    }

    private void loadSpots() {
        spotModel.setRowCount(0);
        int occ = 0;
        String sql = "SELECT s.id, s.spot_number, t.type_name, s.is_occupied, r.hourly_rate FROM parking_spots s JOIN spot_types t ON s.type_id = t.id JOIN tariffs r ON t.id = r.type_id ORDER BY t.type_name, s.spot_number";
        try (Connection conn = DBConnection.getConnection(); ResultSet rs = conn.createStatement().executeQuery(sql)) {
            int num = 1;
            while (rs.next()) {
                boolean isOcc = rs.getBoolean("is_occupied");
                if (isOcc) occ++;
                spotModel.addRow(new Object[]{num++, rs.getString("spot_number"), rs.getString("type_name"), isOcc ? "ЗАНЯТО" : "Свободно", rs.getDouble("hourly_rate"), rs.getInt("id")});
            }
            statsLabel.setText("Авто на парковке: " + occ + " | Свободно мест: " + (spotModel.getRowCount() - occ) + "  ");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadHistory() {
        historyModel.setRowCount(0);
        String sql = "SELECT v.license_plate, v.model, sp.spot_number, tr.hourly_rate, s.entry_time, s.exit_time, s.total_price FROM parking_sessions s JOIN vehicles v ON s.vehicle_id = v.id JOIN parking_spots sp ON s.spot_id = sp.id JOIN tariffs tr ON sp.type_id = tr.type_id ORDER BY s.entry_time DESC";
        try (Connection conn = DBConnection.getConnection(); ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Timestamp out = rs.getTimestamp("exit_time");
                historyModel.addRow(new Object[]{rs.getString("license_plate"), rs.getString("model"), rs.getString("spot_number"), rs.getDouble("hourly_rate"), rs.getTimestamp("entry_time"), (out == null ? "--- НА ПАРКОВКЕ ---" : out), (out == null ? "Ожидает оплаты" : rs.getDouble("total_price") + " руб.")});
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleEntry() {
        // --- ПРОВЕРКА НА ЗАНЯТОСТЬ (ТВОЙ ЗАПРОС) ---
        int selectedRow = spotTable.getSelectedRow();
        if (selectedRow != -1) {
            String currentStatus = spotTable.getValueAt(selectedRow, 3).toString();
            if (currentStatus.equals("ЗАНЯТО")) {
                JOptionPane.showMessageDialog(this, "ОШИБКА: Это место уже занято!\nВыберите свободное место из списка.", "Ошибка регистрации", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Если всё ок, продолжаем регистрацию
        String dbType = "mssql";
        try (FileInputStream in = new FileInputStream("config.properties")) {
            Properties p = new Properties(); p.load(in);
            dbType = p.getProperty("current.db");
        } catch (Exception e) {}

        List<String> cats = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection(); ResultSet rs = conn.createStatement().executeQuery("SELECT type_name FROM spot_types")) {
            while(rs.next()) cats.add(rs.getString("type_name"));
        } catch(Exception e) {}
        JComboBox<String> cb = new JComboBox<>(cats.toArray(new String[0]));
        JTextField fPlate = new JTextField(); JTextField fModel = new JTextField();
        
        if (selectedRow != -1) cb.setSelectedItem(spotTable.getValueAt(selectedRow, 2).toString());

        Object[] msg = {"Тип парковки:", cb, "Госномер автомобиля:", fPlate, "Марка:", fModel};
        if (JOptionPane.showConfirmDialog(this, msg, "Регистрация въезда", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String plate = fPlate.getText().trim();
            if (plate.isEmpty()) return;
            try (Connection conn = DBConnection.getConnection()) {
                String sqlFind;
                if (dbType.equals("mssql")) {
                    sqlFind = "SELECT TOP 1 id FROM parking_spots WHERE is_occupied=0 AND type_id=(SELECT id FROM spot_types WHERE type_name=?) ORDER BY spot_number";
                } else {
                    sqlFind = "SELECT id FROM parking_spots WHERE is_occupied=false AND type_id=(SELECT id FROM spot_types WHERE type_name=?) ORDER BY spot_number LIMIT 1";
                }
                PreparedStatement psF = conn.prepareStatement(sqlFind);
                psF.setString(1, (String)cb.getSelectedItem());
                ResultSet rsS = psF.executeQuery();
                if (!rsS.next()) { JOptionPane.showMessageDialog(this, "Мест нет!"); return; }
                int sId = rsS.getInt("id");

                conn.setAutoCommit(false);
                try {
                    int vId;
                    PreparedStatement psV = conn.prepareStatement("SELECT id FROM vehicles WHERE license_plate=?");
                    psV.setString(1, plate); ResultSet rsV = psV.executeQuery();
                    if (rsV.next()) vId = rsV.getInt("id");
                    else {
                        PreparedStatement insV = conn.prepareStatement("INSERT INTO vehicles (license_plate, model) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
                        insV.setString(1, plate); insV.setString(2, fModel.getText());
                        insV.executeUpdate(); ResultSet rk = insV.getGeneratedKeys(); rk.next(); vId = rk.getInt(1);
                    }
                    PreparedStatement psS = conn.prepareStatement("INSERT INTO parking_sessions (spot_id, vehicle_id, entry_time) VALUES (?,?,CURRENT_TIMESTAMP)");
                    psS.setInt(1, sId); psS.setInt(2, vId); psS.executeUpdate();
                    String updSpotSql = dbType.equals("mssql") ? "UPDATE parking_spots SET is_occupied=1 WHERE id=" + sId : "UPDATE parking_spots SET is_occupied=true WHERE id=" + sId;
                    conn.createStatement().executeUpdate(updSpotSql);
                    conn.commit(); refreshAll();
                } catch (Exception ex) { conn.rollback(); throw ex; }
            } catch (Exception e) { JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage()); }
        }
    }

    private void handleExit() {
        String dbType = "mssql";
        try (FileInputStream in = new FileInputStream("config.properties")) {
            Properties p = new Properties(); p.load(in);
            dbType = p.getProperty("current.db");
        } catch (Exception e) {}

        int row = spotTable.getSelectedRow();
        if (row == -1 || !"ЗАНЯТО".equals(spotTable.getValueAt(row, 3))) {
            JOptionPane.showMessageDialog(this, "Выберите занятое место!"); return;
        }
        int dbId = (int) spotModel.getValueAt(spotTable.convertRowIndexToModel(row), 5);
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT id, entry_time FROM parking_sessions WHERE spot_id=? AND exit_time IS NULL");
            ps.setInt(1, dbId); ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp entry = rs.getTimestamp("entry_time");
                long diffMs = System.currentTimeMillis() - entry.getTime();
                long hActual = diffMs / 3600000;
                long mActual = (diffMs % 3600000) / 60000;
                long hSystem = (long) Math.ceil(diffMs / 3600000.0);
                if (hSystem < 1) hSystem = 1;
                String res = JOptionPane.showInputDialog(this, "Время на парковке: " + hActual + " ч. " + mActual + " мин." + "\nСистема насчитала часов: " + hSystem + "\nВведите часы для оплаты:", hSystem);
                if (res == null) return;
                long finalH = Long.parseLong(res);
                PreparedStatement psR = conn.prepareStatement("SELECT hourly_rate FROM tariffs tr JOIN parking_spots sp ON tr.type_id=sp.type_id WHERE sp.id=?");
                psR.setInt(1, dbId); ResultSet rr = psR.executeQuery(); rr.next();
                double total = finalH * rr.getDouble("hourly_rate");
                if (JOptionPane.showConfirmDialog(this, "К оплате: " + total + " руб. Выезд?") == JOptionPane.YES_OPTION) {
                    conn.setAutoCommit(false);
                    try {
                        PreparedStatement up = conn.prepareStatement("UPDATE parking_sessions SET exit_time=CURRENT_TIMESTAMP, total_price=? WHERE id=?");
                        up.setDouble(1, total); up.setInt(2, rs.getInt("id")); up.executeUpdate();
                        String updSpotSql = dbType.equals("mssql") ? "UPDATE parking_spots SET is_occupied=0 WHERE id=" + dbId : "UPDATE parking_spots SET is_occupied=false WHERE id=" + dbId;
                        conn.createStatement().executeUpdate(updSpotSql);
                        conn.commit(); refreshAll();
                        int rem = 0;
                        String countSql = dbType.equals("mssql") ? "SELECT COUNT(*) FROM parking_spots WHERE is_occupied=1" : "SELECT COUNT(*) FROM parking_spots WHERE is_occupied=true";
                        ResultSet rCnt = conn.createStatement().executeQuery(countSql);
                        if(rCnt.next()) rem = rCnt.getInt(1);
                        JOptionPane.showMessageDialog(this, "Выезд оформлен! На парковке осталось: " + rem + " авто.");
                    } catch (Exception ex) { conn.rollback(); throw ex; }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}