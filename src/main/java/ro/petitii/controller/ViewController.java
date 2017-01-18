package ro.petitii.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import ro.petitii.model.PetitionStatus;

public abstract class ViewController extends BaseController{
    enum ToastType {
        success, info, warning, danger
    }

    protected String i18n(PetitionStatus.Resolution resolution) {
    	return i18n("petition.resolution." + resolution.name().toLowerCase());
    }

    Map<String, String> i18nToast(String key, ToastType type, String... args) {
        return createToast(i18n.i18n(key, args), type);
    }

    Map<String, String> i18nToast(String key, String[] args, ToastType type) {
        return createToast(i18n.i18n(key, args), type);
    }

    Map<String, String> i18nToast(String key, HttpServletRequest request, ToastType type) {
        return createToast(i18n.i18n(key, request), type);
    }

    Map<String, String> i18nToast(String key, ToastType type) {
        return createToast(i18n.i18n(key), type);
    }
    
    
    private Map<String, String> createToast(String message, ToastType type) {
        Map<String, String> toast = new HashMap<>();
        toast.put("message", message);
        toast.put("type", type.name());
        return toast;
    }
}