package br.com.materia.ui;

import br.com.materia.domain.*;
import br.com.materia.service.InventoryService;
import javax.swing.*;
import java.util.List;
import java.awt.*;

final class MovementDialog extends JDialog {
    MovementDialog(MainFrame parent,InventoryService service,Material selected) throws Exception {
        super(parent,"Registrar movimentação",true);
        List<Material> active=service.materials().stream().filter(Material::active).toList();
        if(active.isEmpty())throw new IllegalArgumentException("Cadastre um material ativo antes de movimentar.");
        JComboBox<Material> material=new JComboBox<>(active.toArray(Material[]::new));
        material.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean sel,boolean focus){
            return super.getListCellRendererComponent(list,value instanceof Material m?m.sku()+" • "+m.name():"",index,sel,focus);}});
        if(selected!=null)active.stream().filter(m->m.id()==selected.id()).findFirst().ifPresent(material::setSelectedItem);
        JComboBox<String> type=new JComboBox<>(new String[]{"Entrada","Saída","Ajuste de inventário"});
        JTextField quantity=Ui.input(""),cost=Ui.input(""),reason=Ui.input(""),reference=Ui.input(""),operator=Ui.input(parent.settings().operator());
        JLabel balance=Ui.title("",18);JLabel help=Ui.hint("");JLabel qLabel=new JLabel("Quantidade *");
        Runnable changed=()->{
            Material m=(Material)material.getSelectedItem();if(m==null)return;
            balance.setText("Saldo atual: "+Numbers.qty(m.stock())+" "+m.unit());
            cost.setText(Numbers.decimalMoney(m.costCents()));cost.setEnabled(type.getSelectedIndex()==0);
            boolean adjust=type.getSelectedIndex()==2;
            qLabel.setText(adjust?"Saldo contado no inventário *":"Quantidade *");
            help.setText(adjust?"<html>Informe o saldo final contado. O sistema registra a diferença.<br>Use o motivo para explicar a divergência.</html>":type.getSelectedIndex()==0?"<html>O custo da compra recalcula o custo médio do estoque.<br>Não use separador de milhares.</html>":"<html>A saída usa o custo médio atual. O saldo não pode ficar negativo.<br>Não use separador de milhares.</html>");
        };
        material.addActionListener(e->changed.run());type.addActionListener(e->changed.run());changed.run();
        Ui.Form f=new Ui.Form();f.full("Material *",material);f.full("Estoque",balance);f.full("Tipo de movimentação *",type);
        JPanel qPanel=Ui.panel(new BorderLayout(0,7));qLabel.setLabelFor(quantity);qPanel.add(qLabel,BorderLayout.NORTH);qPanel.add(quantity);
        JPanel pair=Ui.panel(new GridLayout(1,2,12,0));pair.add(qPanel);pair.add(Ui.field("Custo unitário da entrada (R$) *",cost));f.full("Valores",pair);
        f.full("Orientação",help);f.full("Motivo *",reason);f.pair("Documento / referência",reference,"Responsável *",operator);f.finish();
        Ui.dialog(this,f,()->parent.attempt(()->{
            Material m=(Material)material.getSelectedItem();String key=new String[]{"ENTRY","EXIT","ADJUST"}[type.getSelectedIndex()];
            long q=Numbers.quantity(quantity.getText());
            if(key.equals("ADJUST") && !parent.confirm("Confirmar inventário","Alterar o saldo de "+Numbers.qty(m.stock())+" para "+Numbers.qty(q)+" "+m.unit()+"?"))return;
            service.move(m.id(),key,q,key.equals("ENTRY")?Numbers.money(cost.getText()):m.costCents(),reason.getText(),reference.getText(),operator.getText());
            dispose();parent.reload("Movimentação registrada.");
        }));setSize(760,720);setLocationRelativeTo(parent);
    }
}
