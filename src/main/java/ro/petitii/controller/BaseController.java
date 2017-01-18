package ro.petitii.controller;

import org.springframework.beans.factory.annotation.Autowired;

import ro.petitii.util.TranslationUtil;

public abstract class BaseController {

	@Autowired
	TranslationUtil i18n;

	protected String i18n(String key) {
		return this.i18n(key, null);
	}
	
	protected String i18n(String key, String[] params) {
		return i18n.i18n(key, params);		
	}

}
