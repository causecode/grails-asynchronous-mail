package grails.plugin.asyncmail

/**
 * Send asynchronous messages
 */
class AsynchronousMailJob {
    static triggers = {}

    def concurrent = false
    def group = "AsynchronousMail"

    // Dependency injection
    AsynchronousMailProcessService asynchronousMailProcessService

    def execute() {
        log.debug('Entering execute method.')
        def startDate = System.currentTimeMillis()

        asynchronousMailProcessService.findAndSendEmails()

        def endDate = System.currentTimeMillis()
        log.debug("Exiting execute method. Execution time = ${endDate - startDate}ms");
    }
}
