package grails.plugin.asyncmail

class ExpiredMessagesCollectorJob {
    static triggers = {}

    def concurrent = false
    def group = "AsynchronousMail"

    AsynchronousMailPersistenceService asynchronousMailPersistenceService

    def execute() {
        log.debug('Entering execute method.')
        asynchronousMailPersistenceService.updateExpiredMessages()
        log.debug('Exiting execute method.')
    }
}
