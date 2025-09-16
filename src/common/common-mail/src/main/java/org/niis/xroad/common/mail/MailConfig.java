/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.mail;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public MailNotificationProperties mailNotificationProperties() {
        Resource resource = new FileSystemResource(SystemProperties.getConfPath() + "conf.d/mail.yml");
        if (!resource.exists()) {
            log.warn("Configuration {} not exists", resource);
            return new MailNotificationProperties();
        }
        Constructor constructor = createMailYamlConstructor();
        Yaml yaml = new Yaml(constructor);
        try (InputStream input = Files.newInputStream(resource.getFile().toPath())) {
            return yaml.loadAs(input, MailNotificationProperties.class);
        } catch (Exception e) {
            log.warn("Failed to load yaml configuration from {}", resource, e);
            return new MailNotificationProperties();
        }
    }

    private static Constructor createMailYamlConstructor() {
        Constructor constructor = new Constructor(MailNotificationProperties.class, new LoaderOptions());
        TypeDescription mailPropertiesDescriptor = new TypeDescription(MailNotificationProperties.class);
        mailPropertiesDescriptor.substituteProperty("use-ssl-tls",
                boolean.class,
                "isUseSslTls",
                "setUseSslTls");
        constructor.addTypeDescription(mailPropertiesDescriptor);
        return constructor;
    }

    @Bean
    public JavaMailSender mailSender(MailNotificationProperties mailNotificationProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setDefaultEncoding("UTF-8");

        if (mailNotificationProperties != null && mailNotificationProperties.isMailNotificationConfigurationPresent()) {
            mailSender.setHost(mailNotificationProperties.getHost());
            mailSender.setPort(mailNotificationProperties.getPort());
            mailSender.setUsername(mailNotificationProperties.getUsername());
            mailSender.setPassword(mailNotificationProperties.getPassword());
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            if (mailNotificationProperties.isUseSslTls()) {
                props.put("mail.smtp.ssl.enable", "true");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            props.put("mail.smtp.ssl.trust", mailNotificationProperties.getHost());
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "3000");
            props.put("mail.smtp.writetimeout", "5000");
        }

        return mailSender;
    }

    @Bean
    public MessageSource notificationMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setBasename("notifications");
        return messageSource;
    }

    @Bean
    public MessageSourceAccessor notificationMessageSourceAccessor(NotificationConfig notificationConfig) {
        String mailNotificationLocale = notificationConfig.getMailNotificationLocale();

        return mailNotificationLocale != null
                ? new MessageSourceAccessor(notificationMessageSource(), Locale.of(mailNotificationLocale))
                : new MessageSourceAccessor(notificationMessageSource());
    }
}
