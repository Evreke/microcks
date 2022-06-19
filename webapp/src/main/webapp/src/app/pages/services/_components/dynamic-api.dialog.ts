/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal';
import { format } from 'url';

import { Api } from '../../../models/service.model';


@Component({
  selector: 'dynamic-api-dialog',
  templateUrl: './dynamic-api.dialog.html',
  styleUrls: ['./dynamic-api.dialog.css']
})
export class DynamicAPIDialogComponent implements OnInit {
  @Output() createAction = new EventEmitter<Api>();
  
  title: string;
  closeBtnName: string;
  formInvalid: boolean = true;
  api: Api = new Api();
  
  constructor(public bsModalRef: BsModalRef) {}
 
  ngOnInit() {
  }

  createDynamicApi(api: Api) {
    console.log("[DynamicAPIDialogComponent createDynamicApi]");
    if (this.api.resource.startsWith("/")) {
      this.api.resource = this.api.resource.substring(1, this.api.resource.length);
    }
    this.createAction.emit(this.api);
    this.bsModalRef.hide();
  }

  updateApiProperties() {
    if (this.api.name == null || this.api.version == null || this.api.resource == null) {
      this.formInvalid = true;
      return;
    }
    if (this.api.referencePayload != null && this.api.referencePayload.trim() != "") {
      try {
        JSON.parse(this.api.referencePayload)
      } catch (e) {
        this.formInvalid = true;
        return;
      }
    }
    this.formInvalid = false;
  }
}