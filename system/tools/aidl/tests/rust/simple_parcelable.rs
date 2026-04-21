/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Rust implementation of `SimpleParcelable`.

use binder::{
    binder_impl::{
        BorrowedParcel, Deserialize, DeserializeArray, DeserializeOption, Serialize,
        SerializeArray, SerializeOption, NON_NULL_PARCELABLE_FLAG, NULL_PARCELABLE_FLAG,
    },
    StatusCode,
};

/// Rust implementation of `SimpleParcelable`.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct SimpleParcelable {
    /// The name of the parcelable.
    pub name: String,
    /// The number of the parcelable.
    pub number: i32,
}

impl Serialize for SimpleParcelable {
    fn serialize(&self, parcel: &mut BorrowedParcel) -> Result<(), StatusCode> {
        // Parcelables are always treated as optional, because of Java.
        SerializeOption::serialize_option(Some(self), parcel)
    }
}

impl SerializeOption for SimpleParcelable {
    fn serialize_option(
        this: Option<&Self>,
        parcel: &mut BorrowedParcel,
    ) -> Result<(), StatusCode> {
        if let Some(this) = this {
            parcel.write(&NON_NULL_PARCELABLE_FLAG)?;
            parcel.write(&this.name)?;
            parcel.write(&this.number)?;
        } else {
            parcel.write(&NULL_PARCELABLE_FLAG)?;
        }
        Ok(())
    }
}

impl Deserialize for SimpleParcelable {
    type UninitType = Option<Self>;

    fn uninit() -> Option<Self> {
        None
    }

    fn from_init(value: Self) -> Option<Self> {
        Some(value)
    }

    fn deserialize(parcel: &BorrowedParcel) -> Result<Self, StatusCode> {
        // Parcelables are always treated as optional, because of Java.
        DeserializeOption::deserialize_option(parcel)
            .transpose()
            .unwrap_or(Err(StatusCode::UNEXPECTED_NULL))
    }
}

impl DeserializeOption for SimpleParcelable {
    fn deserialize_option(parcel: &BorrowedParcel) -> Result<Option<Self>, StatusCode> {
        let present: i32 = parcel.read()?;
        match present {
            NULL_PARCELABLE_FLAG => Ok(None),
            NON_NULL_PARCELABLE_FLAG => {
                let name = parcel.read()?;
                let number = parcel.read()?;
                Ok(Some(Self { name, number }))
            }
            _ => Err(StatusCode::BAD_VALUE),
        }
    }
}

impl SerializeArray for SimpleParcelable {}

impl DeserializeArray for SimpleParcelable {}
