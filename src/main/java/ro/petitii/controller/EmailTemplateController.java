package ro.petitii.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import ro.petitii.model.EmailTemplate;
import ro.petitii.service.EmailTemplateService;

import javax.validation.Valid;


@Controller
@PreAuthorize("hasAuthority('ADMIN')")
public class EmailTemplateController extends ViewController {
    @Autowired
    private EmailTemplateService emailTemplateService;

    @RequestMapping("/emailTemplates")
    public ModelAndView emailTemplates() {
        ModelAndView modelAndView = new ModelAndView("email_templates_list");
        modelAndView.addObject("apiUrl", "/api/emailTemplates");
        return modelAndView;
    }

    @RequestMapping(path = "/emailTemplate/{id}", method = RequestMethod.GET)
    public ModelAndView addEmailTemplate(@PathVariable("id") Long id) {
        ModelAndView modelAndView = new ModelAndView("email_templates_crud");

        EmailTemplate emailTemplate = emailTemplateService.findOne(id);
        modelAndView.addObject("emailTemplate", emailTemplate);
        modelAndView.addObject("categories", EmailTemplate.Category.values());
        return modelAndView;
    }

    @RequestMapping(path = "/emailTemplate", method = RequestMethod.GET)
    public ModelAndView addEmailTemplate() {
        EmailTemplate emailTemplate = new EmailTemplate();

        ModelAndView modelAndView = new ModelAndView("email_templates_crud");
        modelAndView.addObject("emailTemplate", emailTemplate);
        modelAndView.addObject("categories", EmailTemplate.Category.values());
        return modelAndView;
    }

    @RequestMapping(path = "/emailTemplate/{id}/delete", method = RequestMethod.GET)
    public ModelAndView deleteEmailTemplates(@PathVariable("id") Long id) {
        emailTemplateService.delete(id);
        //TODO: catch exceptions, add  error/success message 
        return new ModelAndView("redirect:/emailTemplates");
    }

    @RequestMapping(path = "/emailTemplate", method = RequestMethod.POST)
    public ModelAndView saveEmailTemplate(@Valid EmailTemplate emailTemplate) {
        emailTemplateService.save(emailTemplate);
        //TODO: catch exceptions, add error/success message 
        return new ModelAndView("redirect:/emailTemplates");
    }
}
