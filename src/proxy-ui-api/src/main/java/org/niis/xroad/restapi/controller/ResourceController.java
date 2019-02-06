/**
 * The MIT License
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
package org.niis.xroad.restapi.controller;

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.restapi.domain.ApiKey;
import org.niis.xroad.restapi.domain.City;
import org.niis.xroad.restapi.repository.ApiKeyRepository;
import org.niis.xroad.restapi.repository.CityRepository;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for some demo rest apis
 */
@RestController
@RequestMapping("/api")
public class ResourceController {

    public static final long CITY_ID = 999L;
    Logger logger = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    /**
     * Return all members
     * @return
     */
    @RequestMapping(value = "/members")
    public List<MemberInfo> getMembers() {
        return clientRepository.getAllMembers();
    }

    /**
     * Return one client, id encoded with ":" (like FI:GOV:1234:SUBSYSTEM1)
     * Breaks since session bound to dao:
     * could not initialize proxy - no Session; nested
     * exception is com.fasterxml.jackson.databind.JsonMappingException: failed to lazily initialize
     * a collection of role: ee.ria.xroad.common.conf.serverconf.model.ClientType.wsdl, could not
     * initialize proxy - no Session (through reference chain: ee.ria.xroad.common.conf.serverconf
     * .model.ClientType[\"wsdl\"]
     * @return
     */
    @RequestMapping(value = "/client/{id}")
    public ClientType getClient(@PathVariable("id") String id) {
        logger.debug("fetching client {} from repository (db)", id);
        ClientType type = clientRepository.getClient(id);
        return type;
    }

    /**
     * Return one client, id encoded with ":" (like FI:GOV:1234:SUBSYSTEM1)
     * @return
     */
    @RequestMapping(value = "/client-id/{id}")
    public ClientId getClientId(@PathVariable("id") String id) {
        logger.debug("fetching client {} from repository (db)", id);
        ClientType type = clientRepository.getClient(id);
        return type.getIdentifier();
    }

    /**
     * resource which returns user's roles.
     * for debugging purposes.
     * @return
     */
    @RequestMapping(value = "/roles")
    public Set<String> getRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = authentication.getAuthorities().stream()
                .map(r -> r.getAuthority()).collect(Collectors.toSet());
        logger.debug("roles =" + roles);
        return roles;
    }

    /**
     * POST resource for testing CSRF
     */
    @PostMapping(value = "/city")
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    public City saveCity(@RequestBody City city) {
        logger.debug("this would save a city {} ", city);
        return city;
    }

    /**
     * service which requires either ROLE_USER or ROLE_ADMIN
     */
    @RequestMapping(value = "/cities")
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')"
            + " or hasAuthority('ROLE_XROAD-SERVICE-ADMINISTRATOR')")
    public List<City> getCities() {
        debugRoles();
        List<City> cities = new ArrayList<>();
        cityRepository.findAll().forEach(cities::add);
        logger.debug("cities: " + cities);
        return cities;
    }

    /**
     * service which requires ROLE_USER
     */
    @RequestMapping(value = "/userCities")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<City> getUserCities() {
        List<City> cities = new ArrayList<>();
        City userCity = new City();
        userCity.setId(CITY_ID);
        userCity.setName("Usercity, from a method which requires 'USER' role");
        cities.add(userCity);
        cityRepository.findAll().forEach(cities::add);
        logger.debug("cities: " + cities);
        return cities;
    }

    private void debugRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = authentication.getAuthorities().stream()
                .map(r -> r.getAuthority()).collect(Collectors.toSet());
        logger.debug("current users roles:" + roles);
    }

    /**
     * service which requires ROLE_ADMIN
     */
    @RequestMapping(value = "/adminCities")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<City> getAdminCities() {
        debugRoles();
        List<City> cities = new ArrayList<>();
        City adminCity = new City();
        adminCity.setId(CITY_ID);
        adminCity.setName("Admincity, from a method which requires 'ADMIN' role");
        cities.add(adminCity);
        cityRepository.findAll().forEach(cities::add);
        logger.debug("cities: " + cities);
        return cities;
    }

    /**
     * create api keys
     */
    @PostMapping(value = "/create-api-key", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ApiKey createKey(@RequestBody List<String> roles) {
        if (roles.isEmpty()) throw new NullPointerException();
        ApiKey key = apiKeyRepository.create(roles);
        logger.debug("created api key " + key.getKey());
        return key;
    }
}
