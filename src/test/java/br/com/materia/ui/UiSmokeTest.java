package br.com.materia.ui;

import br.com.materia.infra.*;
import br.com.materia.domain.*;
import br.com.materia.service.InventoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MATERIA_UI_TEST",matches="1")
class UiSmokeTest {
    @TempDir Path temp;
    @Test void screensDialogsAndCrudWorkOnDesktop() throws Exception {
        Database db=new Database(temp);InventoryService service=new InventoryService(db);service.demo();Settings settings=new Settings(temp);
        AtomicReference<MainFrame> ref=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{Ui.theme(false);MainFrame f=new MainFrame(service,settings);f.setSize(1380,900);f.setVisible(true);ref.set(f);}catch(Exception e){throw new RuntimeException(e);}});
        MainFrame frame=ref.get();Path shots=Path.of("target/ui-qa");Files.createDirectories(shots);
        try{
            for(String page:new String[]{"Visão geral","Materiais","Movimentações","Fornecedores","Relatórios","Sistema"}){
                SwingUtilities.invokeAndWait(()->{frame.showPage(page);frame.validate();});Thread.sleep(120);
                String name=switch(page){case "Visão geral"->"dashboard";case "Movimentações"->"movimentacoes";case "Relatórios"->"relatorios";default->page.toLowerCase();};
                SwingUtilities.invokeAndWait(()->capture(frame,shots.resolve(name+".png")));
            }
            SwingUtilities.invokeAndWait(()->{SupplierDialog d=new SupplierDialog(frame,service,null);field(d,"Nome / razão social *").setText("Fornecedor da interface");field(d,"E-mail").setText("teste@example.com");button(d,"Salvar").doClick();});
            assertEquals(1,service.suppliers().size());
            SwingUtilities.invokeAndWait(()->{try{
                MaterialDialog d=new MaterialDialog(frame,service,null);field(d,"Código *").setText("UI-001");field(d,"Nome *").setText("Material pela interface");field(d,"Estoque inicial *").setText("10,5");field(d,"Custo médio unitário (R$) *").setText("12,50");
                d.setModalityType(Dialog.ModalityType.MODELESS);d.setVisible(true);d.validate();capture(d,shots.resolve("cadastro.png"));button(d,"Salvar").doClick();
            }catch(Exception e){throw new RuntimeException(e);}});
            Material m=service.materials().stream().filter(x->x.sku().equals("UI-001")).findFirst().orElseThrow();assertEquals(10500,m.stock());
            SwingUtilities.invokeAndWait(()->{try{MovementDialog d=new MovementDialog(frame,service,m);field(d,"Quantidade *").setText("2,5");field(d,"Motivo *").setText("Compra pela interface");d.setModalityType(Dialog.ModalityType.MODELESS);d.setVisible(true);d.validate();capture(d,shots.resolve("entrada.png"));button(d,"Salvar").doClick();}catch(Exception e){throw new RuntimeException(e);}});
            assertEquals(13000,service.materials().stream().filter(x->x.id()==m.id()).findFirst().orElseThrow().stock());
            SwingUtilities.invokeAndWait(()->{Ui.theme(true);SwingUtilities.updateComponentTreeUI(frame);frame.showPage("Visão geral");frame.validate();capture(frame,shots.resolve("escuro.png"));});
            SwingUtilities.invokeAndWait(()->{frame.setSize(1100,720);frame.showPage("Sistema");frame.validate();capture(frame,shots.resolve("minimo.png"));});
        }finally{SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
    private static void capture(Window window,Path file){try{BufferedImage im=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();window.paint(g);g.dispose();javax.imageio.ImageIO.write(im,"png",file.toFile());}catch(Exception e){throw new RuntimeException(e);}}
    private static JTextField field(Container root,String label){for(Component c:root.getComponents()){if(c instanceof JLabel l && l.getText().equals(label) && l.getLabelFor() instanceof JTextField f)return f;if(c instanceof Container ct){JTextField f=field(ct,label);if(f!=null)return f;}}return null;}
    private static JButton button(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JButton b && text.equals(b.getText()))return b;if(c instanceof Container ct){JButton b=button(ct,text);if(b!=null)return b;}}return null;}
}
