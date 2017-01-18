package ro.petitii.model.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import ro.petitii.model.EmailTemplate;
import ro.petitii.util.TranslationUtil;

import java.io.IOException;

public class JsonCategorySerializer extends JsonSerializer<EmailTemplate.Category> {
    @Autowired
    private TranslationUtil util;

    @Override
    public void serialize(EmailTemplate.Category category, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
    	
    	String key = "emailTemplate.category." + category.name().toLowerCase();
		gen.writeString(util.i18n(key));
    }
}
