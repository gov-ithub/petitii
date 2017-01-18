package ro.petitii.util;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import ro.petitii.model.EmailTemplate;
import ro.petitii.model.PetitionStatus;

@Component(value = "i18n")
public class TranslationUtil {
    public static final Locale ro = new Locale("ro");

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private LocaleResolver localeResolver;

    public String i18n(String templateId, HttpServletRequest request) {
        return messageSource.getMessage(templateId, null, localeResolver.resolveLocale(request));
    }

    public String i18n(String key, String args[], HttpServletRequest request) {
    	 return messageSource.getMessage(key, args, localeResolver.resolveLocale(request));
    }
    
    public String i18n(String key, String args[]) {
   	 return messageSource.getMessage(key, args, ro);
    }
    
    public String i18n(String key) {
      	 return messageSource.getMessage(key, null, ro);
    }
    

}
