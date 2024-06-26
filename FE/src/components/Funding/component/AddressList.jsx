import React, { useState } from "react";
import AddressCard from "./AddressCard";
import { useStore } from "../../Store/MakeStore";

function AddressList({ listData }) {
  // listData 상태 관리
  const [data, setData] = useState(listData);

  const selectedAddressIndex = useStore((state) => state.selectedAddressIndex);
  const setSelectedAddressIndex = useStore(
    (state) => state.setSelectedAddressIndex,
  );
  const setSelectedAddress = useStore((state) => state.setSelectedAddress);

  // isSelected 상태를 업데이트하는 함수
  const handleSelect = (index) => {
    setSelectedAddress(listData[index]);
    setSelectedAddressIndex(index);
  };

  return (
    <div
      id="addressList"
      className="absolute top-20 w-full flex-col justify-center"
    >
      {listData.map(
        (item, index) => (
          console.log("item " + item),
          (
            <AddressCard
              key={index}
              id={item.id}
              name={item.name}
              isDefault={item.isDefault}
              defaultAddr={item.defaultAddr}
              detailAddr={item.detailAddr}
              zipCode={item.zipCode}
              isSelected={index === selectedAddressIndex}
              onSelect={() => handleSelect(index)} // onSelect prop 추가
            />
          )
        ),
      )}
    </div>
  );
}

export default AddressList;
