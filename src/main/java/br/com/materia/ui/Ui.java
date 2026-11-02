package br.com.materia.ui;

import com.formdev.flatlaf.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public final class Ui {
    public static final Color TEAL=new Color(0x087F75), NAVY=new Color(0x142D35), MUTED=new Color(0x71808C);
    private Ui(){}
    public static void theme(boolean dark){
        if(dark)FlatDarkLaf.setup();else FlatLightLaf.setup();
        UIManager.put("defaultFont",new Font("SansSerif",Font.PLAIN,14));
        UIManager.put("Button.arc",12);UIManager.put("Component.arc",10);UIManager.put("TextComponent.arc",10);
        UIManager.put("Component.focusColor",TEAL);UIManager.put("Component.accentColor",TEAL);
        UIManager.put("Table.rowHeight",43);UIManager.put("Table.showHorizontalLines",true);
        UIManager.put("Table.selectionBackground",dark?new Color(0x1C5651):new Color(0xDCF1EA));
        UIManager.put("Table.selectionForeground",dark?Color.WHITE:NAVY);
        UIManager.put("ScrollBar.width",12);UIManager.put("Button.margin",new Insets(10,16,10,16));
    }
    public static JPanel panel(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    public static JLabel title(String text,int size){JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(Font.BOLD,(float)size));return l;}
    public static boolean isDark(){return UIManager.getLookAndFeel() instanceof FlatDarkLaf;}
    public static Color muted(){return isDark()?new Color(0xB4C3CE):MUTED;}
    public static JLabel hint(String text){JLabel l=new JLabel(text);l.setForeground(muted());return l;}
    public static JButton button(String text,Runnable action){JButton b=new JButton(text);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.addActionListener(e->action.run());return b;}
    public static JButton primary(String text,Runnable action){JButton b=button(text,action);b.setBackground(TEAL);b.setForeground(Color.WHITE);b.putClientProperty("JButton.buttonType","roundRect");return b;}
    public static JPanel card(){JPanel p=new JPanel(new BorderLayout(0,12));p.setBackground(UIManager.getColor("TextField.background"));p.setBorder(new CompoundBorder(new LineBorder(UIManager.getColor("Component.borderColor"),1,true),new EmptyBorder(20,22,20,22)));return p;}
    public static JTextField input(String value){JTextField f=new JTextField(value,20);f.setPreferredSize(new Dimension(220,38));return f;}
    public static void placeholder(JTextField f,String text){f.putClientProperty("JTextField.placeholderText",text);}
    public static JTextArea area(String value){JTextArea a=new JTextArea(value,4,30);a.setLineWrap(true);a.setWrapStyleWord(true);return a;}
    public static void watch(JTextField field,Runnable run){field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){run.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){run.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){run.run();}});}
    public static JPanel field(String label,JComponent input){JPanel p=panel(new BorderLayout(0,7));JLabel l=new JLabel(label);l.setLabelFor(input);p.add(l,BorderLayout.NORTH);p.add(input);return p;}
    public static JTable table(String... columns){
        DefaultTableModel model=new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setFillsViewportHeight(true);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(44);table.getTableHeader().setPreferredSize(new Dimension(0,42));table.getTableHeader().setReorderingAllowed(false);
        table.setIntercellSpacing(new Dimension(12,1));
        DefaultTableCellRenderer renderer=new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focused,int row,int col){
            JLabel l=(JLabel)super.getTableCellRendererComponent(t,v,selected,focused,row,col);l.setBorder(new EmptyBorder(0,9,0,9));l.setToolTipText(v==null?null:v.toString());return l;}};
        table.setDefaultRenderer(Object.class,renderer);table.putClientProperty("JTable.showCellFocusIndicator",false);
        return table;
    }
    public static void rows(JTable table,List<Object[]> rows){DefaultTableModel m=(DefaultTableModel)table.getModel();m.setRowCount(0);for(Object[] row:rows)m.addRow(row);}
    public static void numericSort(JTable table,int...columns){
        var sorter=(TableRowSorter<?>)table.getRowSorter();
        java.util.Comparator<Object> comparator=(a,b)->number(a).compareTo(number(b));
        for(int col:columns)sorter.setComparator(col,comparator);
    }
    private static java.math.BigDecimal number(Object value){String s=value.toString().replaceAll("[^0-9,.-]","").replace(".","").replace(',','.');return new java.math.BigDecimal(s.isBlank()?"0":s);}
    public static void dateSort(JTable table,int column){((TableRowSorter<?>)table.getRowSorter()).setComparator(column,(a,b)->java.time.LocalDateTime.parse(a.toString(),java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")).compareTo(java.time.LocalDateTime.parse(b.toString(),java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));}
    public static void doubleClick(JTable table,Runnable action){table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2 && table.getSelectedRow()>=0)action.run();}});}
    public static void dialog(JDialog d,JComponent form,Runnable save){
        JPanel root=new JPanel(new BorderLayout(0,20));root.setBorder(new EmptyBorder(22,26,22,26));
        JScrollPane scroll=new JScrollPane(form);scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(20);root.add(scroll);
        JPanel bottom=panel(new FlowLayout(FlowLayout.RIGHT,10,0));bottom.add(button("Cancelar",d::dispose));JButton accept=primary("Salvar",save);bottom.add(accept);root.add(bottom,BorderLayout.SOUTH);
        d.setContentPane(root);d.getRootPane().setDefaultButton(accept);d.getRootPane().registerKeyboardAction(e->d.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);d.setMinimumSize(new Dimension(600,500));d.setSize(760,740);d.setLocationRelativeTo(d.getOwner());
    }
    public static class Form extends JPanel {
        private int row=0;
        public Form(){super(new GridBagLayout());setOpaque(false);}
        public void pair(String left,JComponent a,String right,JComponent b){
            addField(left,a,0,1);addField(right,b,1,1);row++;
        }
        public void full(String label,JComponent a){addField(label,a,0,2);row++;}
        private void addField(String label,JComponent input,int x,int width){GridBagConstraints c=new GridBagConstraints();c.gridx=x;c.gridy=row;c.gridwidth=width;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTHWEST;c.insets=new Insets(0,x==0?0:12,17,0);add(field(label,input),c);}
        public void finish(){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.weighty=1;add(Box.createVerticalGlue(),c);}
    }
}
