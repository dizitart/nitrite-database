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

package org.dizitart.no2.integration.repository;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.integration.Retry;
import org.dizitart.no2.integration.repository.data.*;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.rocksdb.RocksDBModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.dizitart.no2.filters.Filter.ALL;
import static org.dizitart.no2.integration.TestUtil.deleteDb;
import static org.dizitart.no2.integration.TestUtil.getRandomTempDbFile;

@RunWith(value = Parameterized.class)
public abstract class BaseObjectRepositoryTest {
    @Parameterized.Parameter
    public boolean isProtected = false;

    protected Nitrite db;
    protected ObjectRepository<Company> companyRepository;
    protected ObjectRepository<Employee> employeeRepository;
    protected ObjectRepository<ClassA> aObjectRepository;
    protected ObjectRepository<ClassC> cObjectRepository;
	protected ObjectRepository<Book> bookRepository;
	
    private final String fileName = getRandomTempDbFile();

	@Rule
    public Retry retry = new Retry(3);

    @Parameterized.Parameters(name = "Protected = {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {false},
            {true},
        });
    }

    @Before
    public void setUp() {
        openDb();

        companyRepository = db.getRepository(Company.class);
        employeeRepository = db.getRepository(Employee.class);

        aObjectRepository = db.getRepository(ClassA.class);
        cObjectRepository = db.getRepository(ClassC.class);

        bookRepository = db.getRepository(Book.class);

        for (int i = 0; i < 10; i++) {
            Company company = DataGenerator.generateCompanyRecord();
            companyRepository.insert(company);
            Employee employee = DataGenerator.generateEmployee();
            employee.setEmpId((long) i + 1);
            employeeRepository.insert(employee);

            aObjectRepository.insert(ClassA.create(i + 50));
            cObjectRepository.insert(ClassC.create(i + 30));

            Book book = DataGenerator.randomBook();
            bookRepository.insert(book);
        }
    }

    protected void openDb() {
        RocksDBModule storeModule = RocksDBModule.withConfig()
            .filePath(fileName)
            .build();

        NitriteBuilder nitriteBuilder = Nitrite.builder()
            .fieldSeparator(".")
            .loadModule(storeModule);

        if (isProtected) {
            db = nitriteBuilder.openOrCreate("test-user", "test-password");
        } else {
            db = nitriteBuilder.openOrCreate();
        }
    }

    @After
    public void clear() throws Exception {
        if (companyRepository != null && !companyRepository.isDropped()) {
            companyRepository.remove(ALL);
        }

        if (employeeRepository != null && !employeeRepository.isDropped()) {
            employeeRepository.remove(ALL);
        }

        if (aObjectRepository != null && !aObjectRepository.isDropped()) {
            aObjectRepository.remove(ALL);
        }

        if (cObjectRepository != null && !cObjectRepository.isDropped()) {
            cObjectRepository.remove(ALL);
        }

        if (bookRepository != null && !bookRepository.isDropped()) {
            bookRepository.remove(ALL);
        }

        if (db != null && !db.isClosed()) {
            db.commit();
            db.close();
        }

        deleteDb(fileName);
    }
}
