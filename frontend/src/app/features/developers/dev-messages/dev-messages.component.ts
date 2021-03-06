import { AfterViewInit, ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { RestService } from '../../../shared/services/rest/rest.service';
import { MessageDTO } from '../../../shared/types/DTOs/input/MessageDTO';
import { SnackbarService } from '../../../shared/services/snackbar-service/snackbar.service';
import { ConfirmationDialogService } from '../../../shared/services/confirmation-dialog/confirmation-dialog.service';
import { ProjectsStoreService } from '../../../stores/projects-store/projects-store.service';
import { UtilsService } from '../../../shared/services/utils-service/utils.service';
import { Project } from '../../../shared/types/entities/Project';
import { TranslateService } from '@ngx-translate/core';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';

@Component({
	selector: 'app-dev-messages',
	templateUrl: './dev-messages.component.html',
	styleUrls: ['./dev-messages.component.scss']
})
export class DevMessagesComponent implements OnInit, AfterViewInit {

	// message form
	messageParams: FormGroup;
	formMode = 'add';
	toUpdate: any = null;
	showForm = false;


	isLoadingResults = true;
	selectedRowIndex = -1;

	projects: any[] = [];
	messages: any[] = [];

	selectedProject = null;
	selectedProjectId = null;
	aggregateInfoElement: any;
	messagesTableElement: any;
	aggregateInfoId = 'aggregateInfoId';
	messagesTableId = 'messagesTable';
	targetLocales: string[] = [];

	// project form
	projectFormMode = 'Add';
	showProjectForm = false;

	// locales select
	@ViewChild('localesSelect') localesSelect: MatSelect;
	selectedLocalesFormControl = new FormControl();
	availableLocales: any[] = [];
	selectedLocales: any[] = [];
	allSelected = false;

	constructor(private formBuilder: FormBuilder,
				private cd: ChangeDetectorRef,
				private http: RestService,
				private snackbar: SnackbarService,
				private projectsStore: ProjectsStoreService,
				private confirmService: ConfirmationDialogService,
				private projectStoreService: ProjectsStoreService,
				private utilsService: UtilsService,
				private translateService: TranslateService,
				private utils: UtilsService) {
		this.selectedProject = this.projectStoreService.getSelectedProject();
	}

	ngOnInit() {
		this.initProjectForm();
		this.getProjects();
		this.getMessages();
	}

	toggleAllSelection() {
		this.allSelected = !this.allSelected;  // to control select-unselect

		if (this.allSelected) {
			this.localesSelect.options.forEach((item: MatOption) => item.select());
		} else {
			this.localesSelect.options.forEach((item: MatOption) => {
				item.deselect();
			});
		}
		this.localesSelect.close();
	}

	initProjectForm() {
		this.messageParams = this.formBuilder.group({
			key: ['', [Validators.required]],
			content: ['', [Validators.required]],
			description: ['']
		});
	}

	createMessage(params: any) {
		const body = new MessageDTO(
			params.value.key,
			params.value.content,
			params.value.description,
			this.selectedProject.id);
		if (!this.toUpdate) {
			this.addMessage(body);
		} else {
			this.updateMessage(body);
		}
	}

	addMessage(body) {
		this.http.save('message/create', body).subscribe((response) => {
			if (response !== null) {
				this.clearForm(this.messageParams);
				this.getMessages();
				this.snackbar.snackSuccess('Success!', 'OK');
			} else {
				this.snackbar.snackError('Error', 'OK');
			}
		}, (error) => {
			this.snackbar.snackError(error.error.message, 'OK');
		});
	}

	updateMessage(body) {
		this.http.update('message/update/' + this.toUpdate.id, body).subscribe((response) => {
			if (response != null) {
				this.toUpdate = null;
				this.clearForm(this.messageParams);
				this.getMessages();
				this.formMode = 'add';
				this.snackbar.snackSuccess('Success!', 'OK');
				this.selectedRowIndex = -1;
				this.toggleForm('');
			} else {
				this.snackbar.snackError('Error', 'OK');
			}
		}, (error) => {
			this.snackbar.snackError(error.error.message, 'OK');
		});
		this.toUpdate = null;
	}

	changeProject(value) {
		this.showProjectForm = false;
		this.cancelUpdate();
		this.selectedProject = value;
		this.targetLocales = [];
		value.targetLocales.forEach((locale) => {
			this.targetLocales.push(locale.locale);
		});
		this.targetLocales.sort();
		this.selectedProjectId = value.id;
		this.projectStoreService.setSelectedProject(value);
		this.getMessages();
		// TODO: add boolean variable to check if any projects are loaded
		this.utilsService.showElement(this.aggregateInfoElement);
		this.utilsService.showElement(this.messagesTableElement);

		this.availableLocales = this.selectedProject.targetLocales;
		this.utils.sortByProperty(this.availableLocales, 'locale');
	}

	async getProjects() {
		this.isLoadingResults = true;
		this.projects = await this.http.getAll('project/getAll');
		this.isLoadingResults = false;
	}

	async getMessages() {
		if (this.selectedProject) {
			console.log('gettting messages');
			this.isLoadingResults = true;
			const response = await this.http.getAll('message/developer/getByProject/' + this.selectedProject.id);
			this.messages = [].concat(response);
			this.selectedProjectId = this.selectedProject.id;
			this.isLoadingResults = false;
		}
	}

	async archiveMessage(id: any) {
		await this.confirmService.openDialog(this.translateService.instant('archiveMessageDialog') + '?').subscribe((result) => {
			if (result) {
				this.http.remove('message/archive/' + id).subscribe((response) => {
					if (response !== null) {
						this.selectedRowIndex = -1;
						this.snackbar.snackSuccess(response.message, 'OK');
					} else {
						this.snackbar.snackError('Error', 'OK');
					}
					this.getMessages();
				}, (error) => {
					this.snackbar.snackError(error.message, 'OK');
				});
			}
		});
	}

	editMessage(message: any) {
		if (this.showForm === false) {
			this.showForm = true;
		}
		this.selectedRowIndex = message.id;
		this.messageParams.patchValue({
			key: message.key,
			content: message.content,
			description: message.description,
			projectId: this.selectedProject.id
		});
		this.toUpdate = message;
		this.formMode = 'update';
	}

	toggleForm($event) {
		this.showForm = !this.showForm;
	}

	cancelUpdate() {
		this.toUpdate = null;
		this.selectedRowIndex = -1;
		this.formMode = 'add';
		this.showForm = false;
		this.clearForm(this.messageParams);
		this.cd.markForCheck();
	}

	clearForm(formGroup: FormGroup) {
		formGroup.reset();
		formGroup.markAsPristine();
		formGroup.markAsUntouched();
	}

	compareProjects(o1: any, o2: any): boolean {
		if (o1 === null || o2 === null) {
			return false;
		} else {
			return o1.name === o2.name;
		}
	}

	ngAfterViewInit(): void {
		this.aggregateInfoElement = document.getElementById(this.aggregateInfoId);
		this.messagesTableElement = document.getElementById(this.messagesTableId);
		if (this.selectedProject == null) {
			this.utilsService.hideElement(this.aggregateInfoElement);
			this.utilsService.hideElement(this.messagesTableElement);
		}
	}

	importCSV($event: any) {
		if ($event.target.files[0]) {
			const url = 'report/import/developer';

			this.http.importCSV(url, $event.target.files[0]).subscribe(response => {
					if (response !== null) {
						this.snackbar.snackSuccess(response, 'OK');
						this.getMessages();
					}
				},
				error => {
					this.snackbar.snackError(error.error, 'OK');
				});
		}
	}

	newProject() {
		this.showProjectForm = true;
	}

	editCurrentProject() {
		this.projectFormMode = 'Update';
		this.showProjectForm = true;
	}

	projectActionComplete(result: Project) {
		this.showProjectForm = false;
		this.projectStoreService.setSelectedProject(result);
		this.selectedProject = result;
		this.getProjects();
		// todo: get messages if project is updated
		this.changeProject(result);
	}

	downloadFullyTranslatedMessages() {
		this.http.downloadTranslationsForAllFullyTranslatedLocales(this.selectedProject.id);
	}

	downloadTranslatedMessagesForGivenLocales() {
		this.selectedLocales = this.selectedLocalesFormControl.value;
		if (this.allSelected) {
			this.selectedLocales = this.selectedLocales.slice(1);
		}
		this.http.downloadTranslationsForSelectedLocales(this.selectedProject.id, this.selectedLocales);
	}
}
