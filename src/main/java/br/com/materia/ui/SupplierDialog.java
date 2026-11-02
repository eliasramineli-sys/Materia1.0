package br.com.materia.ui;

import br.com.materia.domain.Supplier;
import br.com.materia.service.InventoryService;
import javax.swing.*;

final class SupplierDialog extends JDialog {
    SupplierDialog(MainFrame parent,InventoryService service,Supplier original){
        super(parent,original==null?"Novo fornecedor":"Editar fornecedor",true);
        JTextField name=Ui.input(original==null?"":original.name()),document=Ui.input(original==null?"":original.document());
        JTextField contact=Ui.input(original==null?"":original.contact()),phone=Ui.input(original==null?"":original.phone());
        JTextField email=Ui.input(original==null?"":original.email()),address=Ui.input(original==null?"":original.address());
        JTextArea notes=Ui.area(original==null?"":original.notes());
        Ui.Form f=new Ui.Form();f.full("Nome / razão social *",name);f.pair("CPF / CNPJ / documento",document,"Pessoa de contato",contact);
        f.pair("Telefone",phone,"E-mail",email);f.full("Endereço",address);f.full("Observações",new JScrollPane(notes));
        f.full("Documento",Ui.hint("O documento é opcional; o cadastro não consulta a Receita Federal."));f.finish();
        Ui.dialog(this,f,()->parent.attempt(()->{
            service.saveSupplier(new Supplier(original==null?0:original.id(),name.getText(),document.getText(),contact.getText(),phone.getText(),email.getText(),address.getText(),notes.getText(),original==null || original.active()));
            dispose();parent.reload("Fornecedor salvo com sucesso.");
        }));setSize(740,640);setLocationRelativeTo(parent);
    }
}
