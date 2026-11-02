// Launcher Windows Unicode. A aplicação e as regras de negócio são Java.
#ifndef UNICODE
#define UNICODE
#endif
#define _UNICODE
#include <windows.h>
#include <wchar.h>
#include <stdio.h>

static void fail(const wchar_t *message) {
    MessageBoxW(NULL,message,L"Materia • Não foi possível iniciar",MB_OK|MB_ICONERROR);
}
int WINAPI wWinMain(HINSTANCE h,HINSTANCE previous,PWSTR args,int show) {
    wchar_t folder[32768],java[32768],jar[32768],command[65536];
    DWORD length=GetModuleFileNameW(NULL,folder,32768);
    if(!length || length>=32768){fail(L"Caminho do aplicativo inválido.");return 1;}
    wchar_t *slash=wcsrchr(folder,L'\\');if(!slash){fail(L"Pasta do aplicativo não encontrada.");return 1;}*slash=0;
    if(wcslen(folder)>32000){fail(L"Extraia o aplicativo para um caminho mais curto.");return 1;}
    swprintf(java,32768,L"%ls\\runtime\\bin\\javaw.exe",folder);
    swprintf(jar,32768,L"%ls\\app\\materia.jar",folder);
    if(GetFileAttributesW(java)==INVALID_FILE_ATTRIBUTES || GetFileAttributesW(jar)==INVALID_FILE_ATTRIBUTES){
        fail(L"Extraia o ZIP completo antes de abrir o Materia.\n\nMantenha Materia.exe junto às pastas app e runtime.");return 1;
    }
    // Não interpreta argumentos externos; o diretório dos dados é escolhido pela aplicação.
    swprintf(command,65536,L"\"%ls\" -Dfile.encoding=UTF-8 -Xmx512m -jar \"%ls\"",java,jar);
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);PROCESS_INFORMATION process={0};
    if(!CreateProcessW(java,command,NULL,NULL,FALSE,0,NULL,folder,&startup,&process)){
        wchar_t message[300];swprintf(message,300,L"Não foi possível abrir o Java incluído (erro %lu).\nExtraia novamente a pasta completa e tente abrir o aplicativo.",GetLastError());fail(message);return 1;
    }
    CloseHandle(process.hThread);CloseHandle(process.hProcess);return 0;
}
