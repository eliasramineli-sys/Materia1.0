package br.com.materia.ui;

import br.com.materia.domain.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

final class StockChart extends JComponent {
    private final List<Map.Entry<String,BigDecimal>> entries;
    StockChart(List<Material> materials){
        Map<String,BigDecimal> totals=new HashMap<>();
        for(Material m:materials)if(m.active())totals.merge(m.category().isBlank()?"Sem categoria":m.category(),Numbers.valuation(m.stock(),m.costCents()),BigDecimal::add);
        entries=totals.entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed()).limit(6).toList();
        setPreferredSize(new Dimension(520,310));setMinimumSize(new Dimension(300,220));
        getAccessibleContext();
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Font base=UIManager.getFont("Label.font");g.setFont(base.deriveFont(13f));
        if(entries.isEmpty()){g.setColor(Ui.muted());g.drawString("Cadastre materiais para visualizar a distribuição.",12,75);g.dispose();return;}
        double max=Math.max(1,entries.get(0).getValue().doubleValue());int width=Math.max(30,getWidth()-10),y=14;
        int rowHeight=Math.max(28,Math.min(48,getHeight()/entries.size()));
        for(var e:entries){
            g.setColor(UIManager.getColor("Label.foreground"));String label=e.getKey();if(label.length()>32)label=label.substring(0,29)+"…";
            g.drawString(label,0,y);String value=Numbers.currency(e.getValue());g.setColor(Ui.muted());g.drawString(value,Math.max(0,width-g.getFontMetrics().stringWidth(value)),y);
            g.setColor(new Color(Ui.isDark()?0x53666B:0xDCEAE6));g.fillRoundRect(0,y+7,width,9,8,8);g.setColor(Ui.isDark()?new Color(0x62D4C0):Ui.TEAL);g.fillRoundRect(0,y+7,(int)(width*e.getValue().doubleValue()/max),9,8,8);y+=rowHeight;
        }g.dispose();
    }
}
