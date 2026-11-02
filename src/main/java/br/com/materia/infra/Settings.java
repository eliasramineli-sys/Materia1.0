package br.com.materia.infra;

import java.nio.file.*;
import java.io.*;
import java.util.Properties;

public final class Settings {
    private final Path file;
    private final Properties values=new Properties();
    public Settings(Path directory) throws IOException {
        file=directory.resolve("settings.properties");
        if(Files.exists(file))try(var in=Files.newInputStream(file)){values.load(in);}
    }
    public String operator(){return values.getProperty("operator","Operador local");}
    public String company(){return values.getProperty("company","Meu almoxarifado");}
    public boolean dark(){return Boolean.parseBoolean(values.getProperty("dark","false"));}
    public void save(String operator,String company,boolean dark) throws IOException {
        if(operator.strip().length()<2 || operator.strip().length()>80)throw new IllegalArgumentException("Responsável deve ter de 2 a 80 caracteres.");
        if(company.strip().length()<2 || company.strip().length()>100)throw new IllegalArgumentException("Empresa deve ter de 2 a 100 caracteres.");
        values.setProperty("operator",operator.strip());values.setProperty("company",company.strip());values.setProperty("dark",Boolean.toString(dark));
        try(var out=Files.newOutputStream(file)){values.store(out,"Materia - preferencias locais");}
    }
}
