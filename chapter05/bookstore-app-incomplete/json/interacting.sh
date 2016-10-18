#!/bin/bash
export HOSTNAME=boot2docker
export PORT=8080
export ES_PORT=9200
export USER_RESOURCE=api/user
export BOOK_RESOURCE=api/book
export ORDER_RESOURCE=api/order
export USER_ENDPOINT=$HOSTNAME:$PORT/$USER_RESOURCE
export BOOK_ENDPOINT=$HOSTNAME:$PORT/$BOOK_RESOURCE
export ORDER_ENDPOINT=$HOSTNAME:$PORT/$ORDER_RESOURCE

# read model
export USER_MODEL_RESOURCE=user
export INVENTORY_MODEL_RESOURCE=inventory
export ORDER_MODEL_RESOURCE=order
export USER_MODEL_ENDPOINT=$HOSTNAME:$ES_PORT/$USER_MODEL_RESOURCE
export INVENTORY_MODEL_ENDPOINT=$HOSTNAME:$ES_PORT/$INVENTORY_MODEL_RESOURCE
export ORDER_MODEL_ENDPOINT=$HOSTNAME:$ES_PORT/$ORDER_MODEL_RESOURCE


wait() {
while true; do
  if ! nc -z $HOSTNAME $1
  then
    echo "$2 not available, retrying..."
    sleep 1
  else
    echo "$2 is available"
    break;
  fi
done;
}

function clearJournal {
    docker exec -it cassandra cqlsh -k akka -e "truncate table messages;truncate table config;truncate table metadata;"
    docker exec -it cassandra cqlsh -k bookstore -e "truncate table projectionoffsets;"
}

function clearSnapshot {
    docker exec -it cassandra cqlsh -k akka_snapshot -e "truncate table snapshots;"
}

function clearReadModel {
    http -v delete $USER_MODEL_ENDPOINT
    http -v delete $INVENTORY_MODEL_ENDPOINT
    http -v delete $ORDER_MODEL_ENDPOINT
}

function restartBookstore {
    docker restart bookstore
    wait 8080 Bookstore
}

## User API
function createUser {
    http -v post $USER_ENDPOINT < $1
}

function getUserByEmailAddress {
    http -v get $USER_ENDPOINT/$1
}

function editUserByEmailAddress {
    http -v put $USER_ENDPOINT/$1 < $2
}

function deleteUserByEmailAddress {
    http -v delete $USER_ENDPOINT/$1
}

## Book API
function createBook {
    http -v post $BOOK_ENDPOINT < $1
}

function getBookById {
    http -v get $BOOK_ENDPOINT/$1
}

function addTagToBookById {
    http -v put $BOOK_ENDPOINT/$1/tag/$2
}

function removeTagFromBookById {
    http -v delete $BOOK_ENDPOINT/$1/tag/$2
}

function findBookByTag {
    http -v get $BOOK_ENDPOINT tag==$1
}

function findBookByTwoTags {
    http -v get $BOOK_ENDPOINT tag==$1 tag==$2
}

function findBookByAuthor {
    http -v get $BOOK_ENDPOINT author==$1
}

function addNumberOfBooksToInventoryByBookId {
    http -v put $BOOK_ENDPOINT/$1/inventory/$2
}

function deleteBookById {
    http -v delete $BOOK_ENDPOINT/$1
}

## Order API
function createOrder {
    http -v post $ORDER_ENDPOINT < $1
}

function getOrderById {
    http -v get $ORDER_ENDPOINT/$1
}

function getOrdersByEmailAddress {
    http -v get $ORDER_ENDPOINT email==$1
}

function getOrdersByBookId {
    http -v get $ORDER_ENDPOINT bookId==$1
}

function getOrderByBookTag {
    http -v get $ORDER_ENDPOINT bookTag==$1
}

echo "clearing journal, snapshots and read model"
clearJournal
clearSnapshot
clearReadModel
restartBookstore

## Interacting with the user resource
echo "Creating a user"
createUser user.json
echo "Finding a store user by email address"
sleep 1
getUserByEmailAddress chris@masteringakka.com
echo "Editing a store user by email address"
sleep 1
editUserByEmailAddress chris@masteringakka.com user-edit.json
#echo "Deleting a store user by id"
#deleteUserByEmailAddress chris@masteringakka.com

# Interacting with the book resource
echo "Creating a book"
createBook book.json
echo "Getting a book by id"
sleep 1
getBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f
echo "Add tag to book by id"
sleep 1
addTagToBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f ocean
echo "Remove tag from book by id"
sleep 1
removeTagFromBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f ocean
echo "Finding a book by tag"
sleep 10
findBookByTag fiction
echo "Finding a book by two tags"
sleep 1
findBookByTwoTags fiction scifi
echo "Finding a book by author"
sleep 1
findBookByAuthor Verne
echo "Add number of books to inventory by book id"
sleep 1
addNumberOfBooksToInventoryByBookId 798c8b90-e2b3-4d3e-b01e-cf0896531f0f 5
#echo "Deleting a book by id"
#deleteBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f

## Interacting with the order resource
echo "Creating an order"
createOrder order.json
echo "Getting an order by id"
sleep 10
getOrderById b2fc7d0a-92ea-483a-bb59-5bc628f68d9b
echo "Getting orders for a user by user id"
sleep 1
getOrdersByEmailAddress chris@masteringakka.com
echo "Getting orders for a certain book by book id"
sleep 1
getOrdersByBookId 798c8b90-e2b3-4d3e-b01e-cf0896531f0f
echo "Getting orders for books with a certain book tag"
sleep 1
getOrderByBookTag fiction