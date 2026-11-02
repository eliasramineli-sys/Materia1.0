package br.com.materia;

import br.com.materia.domain.*;
import br.com.materia.infra.*;
import br.com.materia.service.InventoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {
    @TempDir Path temp;
    Database db;InventoryService service;
    @BeforeEach void setup() throws Exception {db=new Database(temp.resolve("dados"));service=new InventoryService(db);}
    Material input(String code){return new Material(0,code,"Papel A4","Escritório","UN","A-01",null,"","",1000,1800,5000,0,"Teste",true);}
    Material material(long id) throws Exception{return service.materials().stream().filter(m->m.id()==id).findFirst().orElseThrow();}
    long create(String code,long initial) throws Exception{return service.saveMaterial(input(code),initial,"Elias");}

    @Test void persistsAcrossConnectionsAndReopening() throws Exception {
        long id=create("MAT-001",12000);InventoryService reopened=new InventoryService(new Database(db.directory()));
        assertEquals(12000,reopened.materials().get(0).stock());assertEquals(id,reopened.movements().get(0).materialId());assertEquals("Saldo inicial",reopened.movements().get(0).reason());
    }
    @Test void zeroInitialDoesNotCreateFakeMovement() throws Exception {create("ZERO",0);assertTrue(service.movements().isEmpty());}
    @Test void entryUsesWeightedAverageAndExitPreservesCost() throws Exception {
        long id=create("A",10000);service.move(id,"ENTRY",10000,2000,"Compra","NF 01","Elias");
        assertEquals(1500,material(id).costCents());assertEquals(20000,material(id).stock());
        service.move(id,"EXIT",3000,0,"Consumo","REQ 1","Elias");
        assertEquals(17000,material(id).stock());assertEquals(1500,material(id).costCents());
        Movement last=service.movements().get(0);assertEquals(-3000,last.delta());assertEquals(17000,last.balance());assertEquals(1500,last.costCents());
    }
    @Test void fractionalQuantitiesHaveNoFloatingPointDrift() throws Exception {
        long id=create("FRA",100);service.move(id,"ENTRY",200,1000,"Compra","","Elias");service.move(id,"EXIT",300,0,"Consumo","","Elias");assertEquals(0,material(id).stock());
    }
    @Test void insufficientStockRollsBackAndLeavesNoHistory() throws Exception {
        long id=create("A",1000);assertThrows(IllegalArgumentException.class,()->service.move(id,"EXIT",1001,0,"Consumo","","Elias"));
        assertEquals(1000,material(id).stock());assertEquals(1,service.movements().size());
    }
    @Test void adjustmentUsesTargetAndRejectsNoChange() throws Exception {
        long id=create("A",10000);service.move(id,"ADJUST",7000,0,"Contagem física","INV-01","Elias");assertEquals(-3000,service.movements().get(0).delta());
        assertThrows(IllegalArgumentException.class,()->service.move(id,"ADJUST",7000,0,"Contagem física","","Elias"));
        service.move(id,"ADJUST",0,0,"Zerar inventário","","Elias");assertEquals(0,material(id).stock());
    }
    @Test void duplicateCodeIsCaseInsensitiveAndTransactionSafe() throws Exception {
        create("mat-001",1000);assertThrows(IllegalArgumentException.class,()->create("MAT-001",8000));assertEquals(1,service.materials().size());assertEquals(1,service.movements().size());
    }
    @Test void archiveRequiresZeroStockAndBlocksMovementUntilReactivated() throws Exception {
        long id=create("A",1000);assertThrows(IllegalArgumentException.class,()->service.setMaterialActive(id,false));
        service.move(id,"EXIT",1000,0,"Consumo","","Elias");service.setMaterialActive(id,false);assertFalse(material(id).active());
        assertThrows(IllegalArgumentException.class,()->service.move(id,"ENTRY",1000,100,"Compra","","Elias"));
        service.setMaterialActive(id,true);service.move(id,"ENTRY",1000,100,"Compra","","Elias");assertEquals(3,service.movements().size());
    }
    @Test void editingCannotChangeUnitAfterAnyMovement() throws Exception {
        long id=create("A",1000);Material m=material(id);
        Material edited=new Material(id,m.sku(),m.name(),m.category(),"KG",m.location(),null,"","",1000,1800,5000,0,"",true);
        assertThrows(IllegalArgumentException.class,()->service.saveMaterial(edited,0,"Elias"));assertEquals("UN",material(id).unit());
    }
    @Test void supplierLinkAndNormalizedDocumentArePreserved() throws Exception {
        long supplier=service.saveSupplier(new Supplier(0,"Fornecedor A","12.345.678/0001-99","Ana","11999999999","ana@example.com","Rua A","",true));
        assertThrows(IllegalArgumentException.class,()->service.saveSupplier(new Supplier(0,"Outro","12345678000199","","","","","",true)));
        Material a=input("A");Material linked=new Material(0,a.sku(),a.name(),a.category(),a.unit(),a.location(),supplier,"","",1000,1800,5000,0,"",true);
        long id=service.saveMaterial(linked,0,"Elias");assertEquals("Fornecedor A",material(id).supplierName());
        service.setSupplierActive(supplier,false);assertEquals(supplier,material(id).supplierId());
        Material b=new Material(0,"B",a.name(),a.category(),a.unit(),a.location(),supplier,"","",1000,1800,5000,0,"",true);
        assertThrows(IllegalArgumentException.class,()->service.saveMaterial(b,0,"Elias"));
    }
    @Test void importRollsBackAllRowsIncludingInitialStockWhenConflictOccurs() throws Exception {
        create("EXISTE",0);
        assertThrows(IllegalArgumentException.class,()->service.importMaterials(List.of(input("NOVO"),input("EXISTE")),List.of(1000L,2000L),"Elias"));
        assertEquals(1,service.materials().size());assertTrue(service.movements().isEmpty());
    }
    @Test void movementRequiresReasonAndResponsible() throws Exception {
        long id=create("A",0);assertThrows(IllegalArgumentException.class,()->service.move(id,"ENTRY",1000,100,"","","Elias"));
        assertThrows(IllegalArgumentException.class,()->service.move(id,"ENTRY",1000,100,"Compra","",""));assertEquals(0,material(id).stock());
    }
    @Test void maximumQuantityCannotBeExceeded() throws Exception {
        long id=create("A",Numbers.MAX_QUANTITY);assertThrows(IllegalArgumentException.class,()->service.move(id,"ENTRY",1,1,"Compra","","Elias"));assertEquals(Numbers.MAX_QUANTITY,material(id).stock());
    }
    @Test void backupRestoreRecoversDataAndPreservesSafetyCopy() throws Exception {
        long id=create("A",10000);Path backup=temp.resolve("backup.db");db.backup(backup);
        service.move(id,"EXIT",3000,0,"Consumo","","Elias");db.restore(backup);assertEquals(10000,material(id).stock());assertEquals(1,service.movements().size());
        try(var files=Files.list(db.directory().resolve("backups"))){assertEquals(1,files.filter(p->p.getFileName().toString().startsWith("pre-restauracao-")).count());}
    }
    @Test void invalidBackupIsRejectedWithoutChangingLiveDatabase() throws Exception {
        create("A",1000);Path invalid=temp.resolve("bad.db");Files.writeString(invalid,"não é SQLite");assertThrows(Exception.class,()->db.restore(invalid));assertEquals(1000,service.materials().get(0).stock());
    }
    @Test void inconsistentLedgerBackupIsRejected() throws Exception {
        create("A",1000);Path backup=temp.resolve("bad-ledger.db");db.backup(backup);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+backup);Statement s=c.createStatement()){s.executeUpdate("UPDATE materials SET stock_milli=5000");}
        assertThrows(Exception.class,()->db.restore(backup));assertEquals(1000,service.materials().get(0).stock());
    }
    @Test void backupNeverOverwritesExistingFileOrCurrentDatabase() throws Exception {
        Path p=temp.resolve("backup.db");db.backup(p);assertThrows(IllegalArgumentException.class,()->db.backup(p));assertThrows(IllegalArgumentException.class,()->db.backup(db.file()));
    }
    @Test void dailyBackupIsIdempotent() throws Exception {db.dailyBackup();db.dailyBackup();try(var f=Files.list(db.directory().resolve("backups"))){assertEquals(1,f.count());}}
    @Test void backupRestoreWorksWithSpacesAndAccentsInPath() throws Exception {
        Database local=new Database(temp.resolve("Pasta do usuário & materiais"));InventoryService s=new InventoryService(local);s.saveMaterial(input("CAMINHO"),1000,"Elias");
        Path backup=temp.resolve("cópia com espaços.db");local.backup(backup);local.restore(backup);assertEquals("CAMINHO",s.materials().get(0).sku());
    }
    @Test void demoIsExplicitAndCannotDuplicateOrOverwrite() throws Exception {service.demo();assertEquals(8,service.materials().size());assertThrows(IllegalArgumentException.class,()->service.demo());assertEquals(8,service.materials().size());}
}
