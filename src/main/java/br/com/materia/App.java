package br.com.materia;

import br.com.materia.infra.*;
import br.com.materia.service.InventoryService;
import br.com.materia.ui.*;
import javax.swing.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.logging.*;

public final class App {
    private static FileChannel lockChannel;
    private static FileLock instanceLock;
    private App(){}
    public static void main(String[] args){
        try {
            Path directory=defaultDirectory();
            for(int i=0;i<args.length;i++){
                if(args[i].equals("--data-dir") && i+1<args.length)directory=Path.of(args[++i]);
                else throw new IllegalArgumentException("Argumento inválido. Uso: java -jar materia.jar [--data-dir PASTA]");
            }
            Files.createDirectories(directory);
            lockChannel=FileChannel.open(directory.resolve("materia.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            instanceLock=lockChannel.tryLock();
            if(instanceLock==null)throw new IllegalStateException("O Materia já está aberto para essa pasta de dados.");
            Runtime.getRuntime().addShutdownHook(new Thread(()->{try{if(instanceLock!=null)instanceLock.release();if(lockChannel!=null)lockChannel.close();}catch(Exception ignored){}}));
            FileHandler handler=new FileHandler(directory.resolve("materia-%g.log").toString(),1_000_000,3,true);handler.setFormatter(new SimpleFormatter());Logger.getLogger("Materia").addHandler(handler);
            Database db=new Database(directory);Settings settings=new Settings(directory);String warning="";
            try{db.dailyBackup();}catch(Exception ex){Logger.getLogger("Materia").log(Level.WARNING,"Backup automático não concluído",ex);warning="O backup automático não foi criado. Verifique o espaço em disco e crie um backup em Sistema.";}
            Ui.theme(settings.dark());String startupWarning=warning;
            SwingUtilities.invokeLater(()->{
                try{MainFrame window=new MainFrame(new InventoryService(db),settings);window.setVisible(true);if(!startupWarning.isBlank())JOptionPane.showMessageDialog(window,startupWarning,"Backup",JOptionPane.WARNING_MESSAGE);}
                catch(Exception ex){failure(ex);}
            });
        }catch(Exception ex){failure(ex);}
    }
    public static Path defaultDirectory(){
        String appData=System.getenv("LOCALAPPDATA");
        if(System.getProperty("os.name").toLowerCase().contains("win") && appData!=null && !appData.isBlank())return Path.of(appData,"Materia");
        return Path.of(System.getProperty("user.home"),".local","share","Materia");
    }
    private static void failure(Exception ex){Logger.getLogger("Materia").log(Level.SEVERE,"Falha ao iniciar",ex);if(!java.awt.GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(null,"Não foi possível iniciar o Materia.\n"+ex.getMessage(),"Materia",JOptionPane.ERROR_MESSAGE);System.exit(1);}
}
