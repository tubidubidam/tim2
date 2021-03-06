package org.tim.services;

import org.apache.commons.lang.LocaleUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.tim.configuration.SpringTestsCustomExtension;
import org.tim.entities.LocaleWrapper;
import org.tim.entities.Message;
import org.tim.entities.Project;
import org.tim.entities.Translation;
import org.tim.exceptions.EntityNotFoundException;
import org.tim.repositories.MessageRepository;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ImportServiceTestIT extends SpringTestsCustomExtension {

	@Autowired
	private MessageService messageService;
	@Autowired
	private ImportService importServiceBean;

	@Mock
	private MessageRepository mockedMessageRepository;

	private ImportService importServiceWithMock;
	private TranslationService translationService;

	private Project project;

	private MultipartFile importExampleDevFile = new MockMultipartFile("file.csv", "Test project name,,\nkey,content,description\ntestKey1,testContent1,comment1\ntestKey2,testContent2,comment2 with text".getBytes());
	private MultipartFile importExampleTranFile = new MockMultipartFile("file.csv",
			",message Key,tranKey1,,\n,message Content,Witaj,,\n,message Description,Description,,\nLocale,Translation Status,Translation,Substitute Locale,Substitute Translation\nen_US,Missing,,,\n,New translation,Hello,,\n,,,,\n,message Key,tranKey2,,\n,message Content,Swiecie,,\n,message Description,Description,,\nLocale,Translation Status,Translation,Substitute Locale,Substitute Translation\nen_US,Missing,,,\n,New translation,World,,\n".getBytes());


	@BeforeEach
	void setUp() {
		translationService = new TranslationService(translationRepository, translationVersionRepository, mockedMessageRepository);
		importServiceWithMock = new ImportService(messageService, translationService, projectRepository, mockedMessageRepository, translationRepository);

		project = createEmptyGermanToEnglishProject();
		project.setName("Test project name");
		LocaleWrapper localeWrapperEN = new LocaleWrapper(Locale.US);
		LocaleWrapper localeWrapperUK = new LocaleWrapper(Locale.UK);
		LocaleWrapper localeWrapperPL = new LocaleWrapper(LocaleUtils.toLocale("pl_PL"));
		project.addTargetLocale(Arrays.asList(localeWrapperEN, localeWrapperUK, localeWrapperPL));
		projectRepository.save(project);
	}

	@Test
	@DisplayName("Create messages for project from correct developer file")
	void whenCorrectDeveloperFileImportedThenMessagesAreCreated() throws Exception {
		//given
		//when
		importServiceWithMock.importDeveloperCSVMessage(importExampleDevFile);
		//then
		assertEquals(2, messageRepository.findAll().size());
	}

	@Test
	@DisplayName("When developer file have wrong formatting, rollback transaction and return message")
	void whenWrongFormattingDeveloperCSVFileThenRollback() {
		//given
		MultipartFile importExampleDevFile = new MockMultipartFile("file.csv", "Test project name;;\nkey;content\ntestKey1;testContent1\ntestKey2;testContent2".getBytes());

		//when
		//then
		Exception exception = assertThrows(Exception.class, () -> importServiceWithMock.importDeveloperCSVMessage(importExampleDevFile));
		assertTrue(exception.getMessage().contains(", or check if your delimiter is set to \",\" (comma)"));
		assertEquals(0, mockedMessageRepository.findAll().size());
	}

	@Test
	@DisplayName("When developer file have wrong project name, rollback transaction and return message")
	void whenWrongProjectNameInDeveloperCSVFileThenRollback() {
		//given
		MultipartFile importExampleDevFile = new MockMultipartFile("file.csv", "Wrong project name,,\nkey,content\ntestKey1,testContent1\ntestKey2,testContent2".getBytes());

		//when
		//then
		Exception exception = assertThrows(EntityNotFoundException.class, () -> importServiceWithMock.importDeveloperCSVMessage(importExampleDevFile));
		assertTrue(exception.getMessage().contains("Wrong project name"));
		assertEquals(0, mockedMessageRepository.findAll().size());
	}

	@Test
	@DisplayName("When translation message key isn't found then rollback transaction and return message")
	void whenKeyIsNotFoundInDBFromTranslatorCSVFileThenRollback() throws Exception {
		//given
		saveMessagesToTranslate();
		MockMultipartFile importExampleTranFileWithWrongKey = new MockMultipartFile("file.csv", Files.newInputStream(Paths.get("src/test/resources/reports/testImportWithNotExistingKeys.csv")));
		//when
		//then
		Exception exception = assertThrows(EntityNotFoundException.class, () -> importServiceBean.importTranslatorCSVFile(importExampleTranFileWithWrongKey));
		assertEquals("Sorry, we can't find this notExistingKey", exception.getMessage());
		assertEquals(0, translationRepository.findAll().size());
	}

	@Test
	@DisplayName("When translator file have wrong formatting, rollback transaction and return message")
	void whenWrongFormattingTranslatorCSVFileThenRollback() throws Exception {
		//given
		saveMessagesToTranslate();
		MockMultipartFile importExampleTranFileWithWrongDelimiter = new MockMultipartFile("file.csv", Files.newInputStream(Paths.get("src/test/resources/reports/testImportWithSemicolon.csv")));

		//when
		//then
		Exception exception = assertThrows(Exception.class, () -> importServiceWithMock.importTranslatorCSVFile(importExampleTranFileWithWrongDelimiter));
		assertEquals("Check if your delimiter is set to \",\" (comma)", exception.getMessage());
		assertEquals(0, translationRepository.findAll().size());
	}

	@Test
	@DisplayName("Create translations from file by translator")
	void whenCorrectTranslatorFileImportedThenAddTranslations() throws Exception {
		//given
		MockMultipartFile importFile = new MockMultipartFile("file.csv", Files.newInputStream(Paths.get("src/test/resources/reports/testImportMessageWithTwoLocales.csv")));
		saveMessagesToTranslate();
		//when
		importServiceWithMock.importTranslatorCSVFile(importFile);
		//then
		List<Translation> translations = translationRepository.findAll();

		assertAll(
				() -> assertEquals(3, translations.size()),
				() -> assertEquals("hello", translations.get(0).getContent()),
				() -> assertEquals("witaj", translations.get(1).getContent()),
				() -> assertEquals("world", translations.get(2).getContent())
		);
	}

	@Test
	@DisplayName("Create translations from translator file but skip empty translations")
	void whenTranslatorFileImportedWithEmptyTranslation() throws Exception {
		//given
		MockMultipartFile importFile = new MockMultipartFile("file.csv", Files.newInputStream(Paths.get("src/test/resources/reports/testImportMessageWithEmptyTranslation.csv")));
		saveMessagesToTranslate();

		//when
		importServiceWithMock.importTranslatorCSVFile(importFile);

		//then
		List<Translation> translations = translationRepository.findAll();

		assertAll(
				() -> assertEquals(2, translations.size()),
				() -> assertEquals("witaj", translations.get(0).getContent()),
				() -> assertEquals("world", translations.get(1).getContent())
		);

	}

	private void saveMessagesToTranslate() throws NoSuchFieldException {
		Message messageOne = new Message("hello", "hello", project);
		Message messageTwo = new Message("world", "world", project);

		messageRepository.saveAll(List.of(messageOne, messageTwo));

		FieldSetter.setField(messageOne, messageOne.getClass().getDeclaredField("updateDate"), LocalDateTime.parse("2020-07-31T18:50:41.909720"));
		FieldSetter.setField(messageTwo, messageTwo.getClass().getDeclaredField("updateDate"), LocalDateTime.parse("2020-07-31T18:50:41.909720"));

		when(mockedMessageRepository.findByKey("hello")).thenReturn(Optional.of(messageOne));
		when(mockedMessageRepository.findById(messageOne.getId())).thenReturn(Optional.of(messageOne));


		when(mockedMessageRepository.findByKey("world")).thenReturn(Optional.of(messageTwo));
		when(mockedMessageRepository.findById(messageTwo.getId())).thenReturn(Optional.of(messageTwo));
	}
}
