/*
 * Copyright (c) 2017-2021 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2.integration;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import lombok.Data;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.common.mapper.Mappable;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.repository.annotations.Id;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.util.ArrayList;
import java.util.List;

import static org.dizitart.no2.integration.TestUtil.createDb;


/**
 * @author Anindya Chatterjee
 */
public class NitriteStressTest {
    private static final int TEST_SET_COUNT = 15000;
    private final PodamFactory podamFactory = new PodamFactoryImpl();

    @Rule
    public Retry retry = new Retry(3);

    @Test
    public void stressTest() {
        Nitrite database = createDb();
        ObjectRepository<TestDto> testRepository = database.getRepository(TestDto.class);
        testRepository.createIndex(IndexOptions.indexOptions(IndexType.FULL_TEXT), "lastName");
        testRepository.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "birthDate");

        int counter = 0;
        try {
            for (TestDto testDto : createTestSet()) {
                testRepository.insert(testDto);
                counter++;
            }
        } catch (Throwable t) {
            System.err.println("Crashed after " + counter + " records");
            throw t;
        }
    }

    private List<TestDto> createTestSet() {
        List<TestDto> testData = new ArrayList<>();
        for (int i = 0; i < TEST_SET_COUNT; i++) {
            TestDto testRecords = podamFactory.manufacturePojo(TestDto.class);
            testData.add(testRecords);
        }
        return testData;
    }

    @Data
    public static class TestDto implements Mappable {

        @XmlElement(
            name = "StudentNumber",
            required = true
        )
        @Id
        protected String studentNumber;

        @XmlElement(
            name = "LastName",
            required = true
        )
        protected String lastName;

        @XmlElement(
            name = "Prefixes"
        )
        protected String prefixes;

        @XmlElement(
            name = "Initials",
            required = true
        )
        protected String initials;

        @XmlElement(
            name = "FirstNames"
        )
        protected String firstNames;
        @XmlElement(
            name = "Nickname"
        )
        protected String nickName;

        @XmlElement(
            name = "BirthDate",
            required = true
        )
        @XmlSchemaType(
            name = "date"
        )
        protected String birthDate;


        public TestDto() {
        }

        @Override
        public Document write(NitriteMapper mapper) {
            return Document.createDocument()
                .put("studentNumber", studentNumber)
                .put("lastName", lastName)
                .put("prefixes", prefixes)
                .put("initials", initials)
                .put("firstNames", firstNames)
                .put("nickName", nickName)
                .put("birthDate", birthDate);
        }

        @Override
        public void read(NitriteMapper mapper, Document document) {
            studentNumber = document.get("studentNumber", String.class);
            lastName = document.get("lastName", String.class);
            prefixes = document.get("prefixes", String.class);
            initials = document.get("initials", String.class);
            firstNames = document.get("firstNames", String.class);
            nickName = document.get("nickName", String.class);
            birthDate = document.get("birthDate", String.class);
        }
    }
}
