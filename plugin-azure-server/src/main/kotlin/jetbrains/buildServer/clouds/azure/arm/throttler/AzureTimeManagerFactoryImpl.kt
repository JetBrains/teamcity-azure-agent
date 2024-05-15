package jetbrains.buildServer.clouds.azure.arm.throttler

import org.springframework.beans.factory.BeanFactory

class AzureTimeManagerFactoryImpl(
    private val beanFactory: BeanFactory
) : AzureTimeManagerFactory {
    override fun create(): AzureTimeManager =
        AzureTimeManagerImpl(beanFactory.getBean(AzureTicketTimeManager::class.java), beanFactory.getBean(AzureDefettalSequenceTimeManager::class.java))
}
