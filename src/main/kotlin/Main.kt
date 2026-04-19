package dev.jb.logolsp

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer

fun main() {
    val server = LogoLanguageServer()
    val launcher = Launcher.createLauncher(server, LanguageClient::class.java, System.`in`, System.out)
    server.connect(launcher.remoteProxy as LanguageClient)
    launcher.startListening().get()
}

