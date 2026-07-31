package com.guslarz.invoicewatcher.mailproducer.dao.firestore;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper.FirestoreMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.FolderConfigDaoServiceFirestoreImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.UserDaoServiceFirestoreImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    UserDaoServiceFirestoreImpl.class,
    FolderConfigDaoServiceFirestoreImpl.class,
    FirestoreMapper.class
})
@ConditionalOnProperty(
    prefix = MailProducerDaoProperties.Constants.Prefix,
    name = MailProducerDaoProperties.Constants.Mode,
    havingValue = "firestore"
)
public class MailProducerDaoFirestoreAutoConfiguration {
}
