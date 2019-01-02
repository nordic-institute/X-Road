package org.niis.xroad.authproto.controller;

import org.niis.xroad.authproto.domain.ApiKey;
import org.niis.xroad.authproto.domain.City;
import org.niis.xroad.authproto.repository.ApiKeyRepository;
import org.niis.xroad.authproto.repository.CityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/test-api")
public class ResourceController {

    Logger logger = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    /**
     * TODO: this is just for debugging, remove from actual implementation
     */
    @RestController
    public class CsrfController {
        @RequestMapping("/csrf")
        public CsrfToken csrf(CsrfToken token) {
            return token;
        }
    }

    /**
     * resource which returns user's roles.
     * for debugging purposes.
     * @return
     */
    @RequestMapping(value ="/roles")
    public Set<String> getRoles(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = authentication.getAuthorities().stream()
                .map(r -> r.getAuthority()).collect(Collectors.toSet());
        logger.info("roles=" + roles);
        return roles;
    }

    /**
     * POST resource for testing CSRF
     */
    @PostMapping(value ="/city")
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    public City saveCity(@RequestBody City city){
        logger.info("this would save a city {} ", city);
        return city;
    }

    /**
     * service which requires either ROLE_USER or ROLE_ADMIN
     */
    @RequestMapping(value ="/cities")
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    public List<City> getCities(){
        debugRoles();
        List<City> cities = new ArrayList<>();
        cityRepository.findAll().forEach(cities::add);
        logger.info("cities: " + cities);
        return cities;
    }

    /**
     * service which requires ROLE_USER
     */
    @RequestMapping(value ="/userCities")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<City> getUserCities(){
        List<City> cities = new ArrayList<>();
        City userCity = new City();
        userCity.setId(999L);
        userCity.setName("Usercity, from a method which requires 'USER' role");
        cities.add(userCity);
        cityRepository.findAll().forEach(cities::add);
        logger.info("cities: " + cities);
        return cities;
    }

    private void debugRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = authentication.getAuthorities().stream()
                .map(r -> r.getAuthority()).collect(Collectors.toSet());
        logger.info("current users roles:" + roles);
    }

    /**
     * service which requires ROLE_ADMIN
     */
    @RequestMapping(value ="/adminCities")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<City> getAdminCities(){
        debugRoles();
        List<City> cities = new ArrayList<>();
        City adminCity = new City();
        adminCity.setId(999L);
        adminCity.setName("Admincity, from a method which requires 'ADMIN' role");
        cities.add(adminCity);
        cityRepository.findAll().forEach(cities::add);
        logger.info("cities: " + cities);
        return cities;
    }

    /**
     * create api keys using this service
     * GET http://localhost:8080/create-api-key/role1,role2,role3
     * This is probably wrong from the api guidelines viewpoint...?
     */
    @PostMapping(value ="/create-api-key", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ApiKey createKey(@RequestBody List<String> roles){
        if (roles.isEmpty()) throw new NullPointerException();
        ApiKey key = apiKeyRepository.create(roles);
        logger.debug("created api key " + key.getKey());
        return key;
    }
}
