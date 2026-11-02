package br.com.materia.ui;

import br.com.materia.domain.*;
import br.com.materia.service.InventoryService;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

final class MaterialDialog extends JDialog {
    MaterialDialog(MainFrame parent,InventoryService service,Material original) throws Exception {
        super(parent,original==null?"Novo material":"Editar material • "+original.sku(),true);
        JTextField sku=Ui.input(original==null?"":original.sku());
        JTextField name=Ui.input(original==null?"":original.name());
        TreeSet<String> categories=new TreeSet<>();service.materials().stream().map(Material::category).filter(s->!s.isBlank()).forEach(categories::add);
        JComboBox<String> category=new JComboBox<>(categories.toArray(String[]::new));category.setEditable(true);category.setSelectedItem(original==null?"":original.category());
        JComboBox<String> unit=new JComboBox<>(InventoryService.UNITS.toArray(String[]::new));unit.setSelectedItem(original==null?"UN":original.unit());
        JTextField location=Ui.input(original==null?"":original.location()), barcode=Ui.input(original==null?"":original.barcode());
        JComboBox<Object> supplier=new JComboBox<>();supplier.addItem("Sem fornecedor");
        for(Supplier s:service.suppliers())if(s.active() || (original!=null && Objects.equals(s.id(),original.supplierId())))supplier.addItem(s);
        if(original!=null && original.supplierId()!=null)for(int i=0;i<supplier.getItemCount();i++)if(supplier.getItemAt(i) instanceof Supplier s && s.id()==original.supplierId())supplier.setSelectedIndex(i);
        JTextField cost=Ui.input(original==null?"0,00":Numbers.decimalMoney(original.costCents())),price=Ui.input(original==null?"0,00":Numbers.decimalMoney(original.priceCents()));
        JTextField minimum=Ui.input(original==null?"0":Numbers.qty(original.minimum())),initial=Ui.input(original==null?"0":Numbers.qty(original.stock()));
        initial.setEditable(original==null);
        JTextArea notes=Ui.area(original==null?"":original.notes());
        Ui.placeholder(sku,"Ex.: MAT-001");Ui.placeholder(name,"Nome do material");Ui.placeholder(location,"Ex.: Corredor A / Prateleira 02");
        Ui.Form f=new Ui.Form();f.full("Identificação",Ui.hint("* Campos obrigatórios · valores sem separador de milhares"));
        f.pair("Código *",sku,"Nome *",name);f.pair("Categoria",category,"Unidade *",unit);
        f.pair("Localização",location,"Código de barras",barcode);f.full("Fornecedor principal",supplier);
        f.pair("Custo médio unitário (R$) *",cost,"Preço de referência (R$) *",price);
        f.pair("Estoque mínimo *",minimum,original==null?"Estoque inicial *":"Saldo atual (somente leitura)",initial);
        f.full("Como o estoque funciona",Ui.hint("<html>Quantidades: até 3 casas decimais. Valores: até 2 casas.<br>Entradas recalculam o custo médio ponderado. Altere o saldo em Movimentações.</html>"));
        f.full("Observações",new JScrollPane(notes));f.finish();
        Ui.dialog(this,f,()->parent.attempt(()->{
            Object s=supplier.getSelectedItem();
            Material m=new Material(original==null?0:original.id(),sku.getText(),name.getText(),Objects.toString(category.getSelectedItem(),""),(String)unit.getSelectedItem(),location.getText(),s instanceof Supplier sp?sp.id():null,"",barcode.getText(),Numbers.money(cost.getText()),Numbers.money(price.getText()),Numbers.quantity(minimum.getText()),0,notes.getText(),true);
            service.saveMaterial(m,original==null?Numbers.quantity(initial.getText()):0,parent.settings().operator());
            dispose();parent.reload("Material salvo com sucesso.");
        }));
    }
}
