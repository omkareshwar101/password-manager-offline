package com.offlinevault.app.ui.navigation

object Routes {
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ADD = "add"
    const val ACCOUNT = "account/{accountId}"
    const val EDIT = "edit/{accountId}"
    const val SECURITY_QUESTIONS = "security_questions"
    const val BACKUP = "backup"

    const val ACCOUNT_ID_ARG = "accountId"

    fun account(accountId: Long): String = "account/$accountId"
    fun edit(accountId: Long): String = "edit/$accountId"
}
