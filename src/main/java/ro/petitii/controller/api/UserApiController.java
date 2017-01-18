package ro.petitii.controller.api;

import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ro.petitii.controller.BaseController;
import ro.petitii.model.User;
import ro.petitii.service.UserService;

import javax.validation.Valid;

@RestController
public class UserApiController extends BaseController{
    @Autowired
    private UserService userService;

    @JsonView(DataTablesOutput.View.class)
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(value = "/api/users", method = {RequestMethod.GET, RequestMethod.POST})
    public DataTablesOutput<User> getUsers(@Valid DataTablesInput input) {
    	//TODO: catch exceptions, add  error/success message
        return userService.findAll(input);
    }
}