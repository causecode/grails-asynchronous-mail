package grails.plugin.asyncmail

import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.plugin.asyncmail.enums.MessageStatus
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
import org.springframework.mail.MailParseException
import org.springframework.mail.MailPreparationException

import static grails.async.Promises.task

class AsynchronousMailProcessService implements GrailsConfigurationAware {
    static transactional = false

    Config configuration

    def asynchronousMailPersistenceService
    def asynchronousMailSendService

    public void findAndSendEmails() {
        // Get messages from DB
        def messagesIds = asynchronousMailPersistenceService.selectMessagesIdsForSend()

        // Send each message and save new status
        messagesIds.each { Long messageId ->
            task {
                AsynchronousMailMessage.withNewSession {
                    try {
                        processEmailMessage(messageId)
                    } catch (Exception e) {
                        log.error('Abort mail sent.', e)
                    }
                }
            }
        }
    }

    void processEmailMessage(long messageId) {
        boolean useFlushOnSave = configuration.asynchronous.mail.useFlushOnSave

        def message = asynchronousMailPersistenceService.getMessage(messageId)
        log.debug("Found a message: " + message.toString())

        Date now = new Date()
        Date attemptDate = new Date(now.getTime() - message.attemptInterval)
        boolean canAttempt = message.hasAttemptedStatus() && message.lastAttemptDate.before(attemptDate)
        if (message.hasCreatedStatus() || canAttempt) {
            message.lastAttemptDate = now
            message.attemptsCount++

            // Guarantee that e-mail can't be sent more than 1 time
            message.status = MessageStatus.ERROR
            asynchronousMailPersistenceService.save(message, useFlushOnSave)

            // Attempt to send
            try {
                log.debug("Attempt to send the message with id=${message.id}.")
                asynchronousMailSendService.send(message)
                message.sentDate = now
                message.status = MessageStatus.SENT
                log.debug("The message with id=${message.id} was sent successfully.")
            } catch (MailException e) {
                log.warn("Attempt to send the message with id=${message.id} was failed.", e)
                canAttempt = message.attemptsCount < message.maxAttemptsCount
                boolean fatalException = e instanceof MailParseException || e instanceof MailPreparationException
                if (canAttempt && !fatalException) {
                    message.status = MessageStatus.ATTEMPTED
                }

                if (e instanceof MailAuthenticationException) {
                    throw e
                }
            } finally {
                log.debug "Saving the message $message"
                asynchronousMailPersistenceService.save(message, useFlushOnSave)
            }

            // Delete message if it is sent successfully and can be deleted
            if (message.hasSentStatus() && message.markDelete) {
                long id = message.id
                asynchronousMailPersistenceService.delete(message);
                log.debug("The message with id=${id} was deleted.")
            }
        }
    }
}
